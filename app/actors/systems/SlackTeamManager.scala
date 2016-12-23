package actors.systems

import actors.BotMessages
import actors.BotMessages.BotMessages
import actors.bots.ShoppingBotActor
import actors.systems.DRActor.NewUserSessionReply
import actors.systems.SlackTeamManager._
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import play.api.Configuration
import slack.models.Attachment
import slack.rtm.SlackAPIActor
import slack.rtm.SlackAPIActor.TeamNameResponse
import utils.DRAPI
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.Inject
import models.SlackSessionRepo

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Created by iholsman on 12/20/2016.
  */
class SlackTeamManager @Inject()(implicit ec: ExecutionContext) extends Actor with ActorLogging {
  implicit val timeout: Timeout = 5.seconds
  val duration: FiniteDuration = 5.seconds

  override def receive: Receive = {
    case SlackTeamManager.Load(repo, drActor) =>
      self ! Load(repo, drActor)
      context.become(receive_running(repo, Map.empty, Map.empty, Map.empty))
    case GetSlackByTeam(teamId: String) =>
      log.info("GetSlackByTeam....?....")
    case SlackTeamManager.ConnectShopperToSlack(token: String, drActor: ActorRef) =>
      log.error("SlackTeamManager OutOfSync. ConnectShopperToSlack message not expected. Need to LOAD")
    case SlackTeamManager.TeamNameInfo => log.error("SlackTeamManager OutOfSync. TeamNameInfo message not expected")
    case _ => log.info("Unknown Message SlackTeamManager")
  }

  def receive_running(slackSessionRepo: SlackSessionRepo, sessionsActor: Map[String, SessionEntry], actorSession: Map[ActorRef, String], slackActorSession: Map[ActorRef, String]): Receive = {
    case SlackTeamManager.Load(repo, drActor) =>
      repo.all.map {
        sessions => sessions.foreach(session => self ! SlackTeamManager.ConnectShopperToSlack(session.token, drActor))
      }
    case SlackTeamManager.ConnectShopperToSlack(token: String, drActor: ActorRef) =>
      log.debug(s"Attempt to Connect shopper to slack Token = $token")
      val slackActor: ActorRef = context.actorOf(SlackAPIActor.props(token), s"Slack_$token")

      (slackActor ? SlackAPIActor.TeamName).mapTo[TeamNameResponse].map { tnr =>
        self ! SlackTeamManager.TeamNameInfo(slackActor, drActor, token, tnr)
      }.recover {
        case fail => log.error(s"Can't get TeamName Info  ${fail.getMessage}")
      }

    case SlackTeamManager.TeamNameInfo(slackActor, drActor, token, teamInfo) =>
      log.debug(s"Attempt - TNI -  to Connect shopper to slack Token = $token info = $teamInfo")
      val shoppingBotActor = context.actorOf(ShoppingBotActor.props(slackActor, drActor), s"Shopping_${teamInfo.id}")
      slackSessionRepo.create(teamInfo.id, token)
      shoppingBotActor ! BotMessages.Start
      context.watch(shoppingBotActor)
      context.become(receive_running(slackSessionRepo,
        sessionsActor + (teamInfo.id -> SessionEntry(slackActor, shoppingBotActor)),
        actorSession + (shoppingBotActor -> teamInfo.id),
        slackActorSession + (slackActor -> teamInfo.id)))

    case Terminated(actorRef) =>
      actorSession.get(actorRef) match {
        case Some(teamId) =>
          sessionsActor.get(teamId) match {
            case Some(sessionEntry) => context.become(receive_running(slackSessionRepo, sessionsActor - teamId, actorSession - actorRef, slackActorSession - sessionEntry.slackEntry))
            case None =>
              log.error(s"$teamId out of sync. couldn't find entry")
          }

        case None =>
          log.error(s"Couldn't find ActorRef $actorRef in list of Slack connections")
      }

    case RemoveTeam(teamId: String) =>
      sessionsActor.get(teamId) match {
        case Some(actorRef) =>
          slackSessionRepo.deleteByTeamId(teamId)
          actorRef.botEntry ! PoisonPill
          actorRef.slackEntry ! PoisonPill
          context.become(receive_running(slackSessionRepo, sessionsActor - teamId, actorSession - actorRef.botEntry, slackActorSession - actorRef.slackEntry))
        case None => log.info(s"TeamID $teamId not found")
      }
    case GetByTeam(teamId: String) =>
      sessionsActor.get(teamId) match {
        case Some(actorRef) => sender() ! actorRef.botEntry
        case None => log.info(s"TeamID $teamId not found")
      }
    case GetSlackByTeam(teamId: String) =>
      sessionsActor.get(teamId) match {
        case Some(actorRef) => sender() ! actorRef.slackEntry
        case None => log.info(s"TeamID $teamId not found")
      }
    case OpenSessions =>
      val keys: Seq[String] = sessionsActor.keys.toSeq
      log.info("OpenSessions - Keys")
      sender() ! keys

    case _ => log.info("Unknown Message SlackTeamManager")
  }

  case class SessionEntry(slackEntry: ActorRef, botEntry: ActorRef)


}


object SlackTeamManager {
  def props()(implicit ec: ExecutionContext): Props = Props(classOf[SlackTeamManager])

  sealed trait SlackTeamMessage extends BotMessages

  case class Load(slackSessionRepo: SlackSessionRepo, drActor: ActorRef) extends SlackTeamMessage

  case class ConnectShopperToSlack(token: String, drActor: ActorRef) extends SlackTeamMessage

  case class RemoveTeam(id: String) extends SlackTeamMessage

  case class GetByTeam(id: String) extends SlackTeamMessage

  case class GetSlackByTeam(id: String) extends SlackTeamMessage

  protected case class TeamNameInfo(slackAPIActor: ActorRef, drActor: ActorRef, token: String, TeamInfo: TeamNameResponse)

  case object OpenSessions extends SlackTeamMessage

}
