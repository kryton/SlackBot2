package actors.bots

import javax.inject.{Inject, Named}

import actors.BotMessages.{BotMessages, Start}
import actors.systems
import actors.systems.ServiceManagerActor
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import play.api.Configuration
import slack.models.{ActionField, Attachment}
import slack.rtm.SlackAPIActor
import utils.IncidentDetail

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
  * Created by iholsman on 11/7/2016.
  */
class IncidentActor @Inject()(@Named("SlackAPI-actor") slackAPIActor: ActorRef,
                              @Named("ServiceManager-actor") serviceManagerActor: ActorRef,
                              configuration: Configuration)(implicit ec: ExecutionContext) extends Actor with ActorLogging {
  implicit val timeout: Timeout = 5.seconds
  val mainChannelID="C31NJ1KLK"
  override def preStart(): Unit = {
    super.preStart()
    self ! Start
  }
  override def receive: Receive = {

    case Start =>
      slackAPIActor ! SlackAPIActor.AddListener(self)
      serviceManagerActor ! Start
      log.info("Incident Started")
    case r:SlackAPIActor.RecvMessage => parseMessage(r)
    case IncidentActor.Tick =>
        val incidentSeqF = ( serviceManagerActor ? ServiceManagerActor.OpenP1P2).mapTo[Seq[String]].map {
          incidents:Seq[String] =>
            log.error(s"Got List of Open P1P2s - #${incidents.size}")
            val details : Seq[Future[Option[IncidentDetail]]] = incidents.map {
              id => (serviceManagerActor ? ServiceManagerActor.Incident(id)).mapTo[Option[IncidentDetail]]
            }
            Future.sequence(details)
        }.flatMap(identity)
        incidentSeqF.map {
          incidentSeqO:Seq[Option[IncidentDetail]] =>
          log.error("Got list of Incidents")
          val incidentSeq:Seq[IncidentDetail] = incidentSeqO.flatten
          val actionField = Seq(
            ActionField("open", "Open/Goto Channel", "button", Some("primary")),
            ActionField("detail", "Detail", "button", ),
            ActionField("assign", "AssignToMe", "button")
          )
          val attachments: Seq[Attachment] = incidentSeq.map { incident: IncidentDetail =>
            Attachment(text = Some(s"${incident.IncidentID} - ${incident.Title}"),
              fallback = Some("backup message: code-123456"),
              callback_id = Some(s"IM-Open-${incident.IncidentID}"), actions = actionField)
          }
          log.error("Sending it to Slack")
          slackAPIActor ! SlackAPIActor.SendAttachment( channel = mainChannelID, text = "Open P1/P2 Incidents", attachments = attachments)
      }

      log.error("TICK")
    case _ => log.error("Unknown message")
  }

  def parseMessage( msg:SlackAPIActor.RecvMessage) = {
    val IMpattern = "^.*(IM[0-9]+).*$".r
    if (msg.text.startsWith("$")) {
      msg.text match {
        case IMpattern(incident) =>
          (serviceManagerActor ? ServiceManagerActor.Incident(incident)).mapTo[Option[IncidentDetail]].map {
            case None =>  slackAPIActor ! SlackAPIActor.SendMessage(msg.channel, s"$incident - Incident Not found")
            case Some(i: IncidentDetail) =>
              slackAPIActor ! SlackAPIActor.SendMessage(msg.channel, s"${i.IncidentID} - ${i.Title}\n${i.Description.foldLeft("")(_+_.getOrElse("")+"\n")}")
          }

        case _ => slackAPIActor ! SlackAPIActor.SendMessage(msg.channel, s"I don't understand -  ${msg.text}")
      }
    }
  }
}


object IncidentActor  {
  def props( slackAPIActor: ActorRef, serviceManagerActor: ActorRef,configuration: Configuration)(implicit ec: ExecutionContext) : Props =
      Props(classOf[IncidentActor], slackAPIActor, serviceManagerActor, configuration)

  sealed trait IncidentActorMessage extends BotMessages
  case class Config(friendlyName:String) extends IncidentActorMessage
  case object Tick extends IncidentActorMessage
}
