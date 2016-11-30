package actors.bots

import javax.inject.{Inject, Named}

import actors.BotMessages.{BotMessages, Start}
import actors.systems
import actors.systems.{ServiceManagerActor, SlackAPIActor}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import play.api.Configuration
import utils.IncidentDetail

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Created by iholsman on 11/7/2016.
  */
class IncidentActor @Inject()(@Named("SlackAPI-actor") slackAPIActor: ActorRef,
                              @Named("ServiceManager-actor") serviceManagerActor: ActorRef,
                              configuration: Configuration)(implicit ec: ExecutionContext) extends Actor with ActorLogging {
  implicit val timeout: Timeout = 5.seconds
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
    case _ => log.error("Unknown message")
  }

  def parseMessage( msg:SlackAPIActor.RecvMessage) = {
    val IMpattern = "^.*(IM[0-9]+).*$".r
    if (msg.text.startsWith("$")) {
      msg.text match {
        case IMpattern(incident) =>
          (serviceManagerActor ? ServiceManagerActor.Incident(incident)).mapTo[Option[IncidentDetail]].map {
            case None =>  slackAPIActor ! systems.SlackAPIActor.SendMessage(msg.channel, s"$incident - Incident Not found")
            case Some(i: IncidentDetail) =>
              slackAPIActor ! systems.SlackAPIActor.SendMessage(msg.channel, s"${i.IncidentID} - ${i.Title}\n${i.Description.foldLeft("")(_+_.getOrElse("")+"\n")}")
          }

        case _ => slackAPIActor ! systems.SlackAPIActor.SendMessage(msg.channel, s"I don't understand -  ${msg.text}")
      }
    }
  }
}


object IncidentActor  {
  def props( slackAPIActor: ActorRef, serviceManagerActor: ActorRef,configuration: Configuration)(implicit ec: ExecutionContext) : Props =
      Props(classOf[IncidentActor], slackAPIActor, serviceManagerActor, configuration)

  sealed trait IncidentActorMessage extends BotMessages
  case class Config(friendlyName:String)
}
