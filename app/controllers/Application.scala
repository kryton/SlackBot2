package controllers

import javax.inject._

import actors.BotMessages.{End, GetConfig, Start}
import actors.systems.{ServiceManagerActor, SlackAPIActor}
import akka.actor.ActorRef
import akka.http.scaladsl.model.DateTime
import akka.pattern.ask
import akka.util.Timeout
import play.api.mvc._
import slack.models.Channel
import utils.{IncidentDetail, ServiceManagerAPI}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Application @Inject()(@Named("SlackAPI-actor") slackAPIActor: ActorRef,
                            @Named("ServiceManager-actor") serviceManagerActor: ActorRef,
                            @Named("Incident-actor") incidentActor: ActorRef,
                            @Named("DR-actor") drActor: ActorRef,
                            @Named("ShoppingBot-actor") shoppingActor: ActorRef
                           )(implicit ec: ExecutionContext) extends Controller {

  def index = Action.async {
    Future(Ok(views.html.index("Your new application is ready.")))
  }

  implicit val timeout: Timeout = 5.seconds

  def slackGetConfig = Action.async {
    (slackAPIActor ? GetConfig).mapTo[String].map { message =>
      Ok(message)
    }
  }

  def IMstart: Action[AnyContent] = Action.async {
    incidentActor ! Start
    Future(Ok("Attempted to start IM Bot"))
  }
  def shoppingBotStart = Action.async {
    shoppingActor ! Start
    Future(Ok("Attempted to start Shopping Bot"))
  }

  def slackStart = Action.async {
    slackAPIActor ! Start
    Future(Ok("Attempted to Start"))
  }

  def slackEnd = Action.async {
    slackAPIActor ! End
    Future(Ok("Attempted to End"))
  }

  def slackChannels = Action.async {
    (slackAPIActor ? SlackAPIActor.Channels).mapTo[Seq[slack.models.Channel]].map { message =>
      Ok(message.foldLeft("")((x: String, y: Channel) => x + s"${y.id}/${y.name}\n"))
    }
  }

  def slackUsers = Action.async {
    (slackAPIActor ? SlackAPIActor.Users).mapTo[Seq[slack.models.User]].map { message =>
      Ok(message.foldLeft("")((x: String, y: slack.models.User) => x + s"${y.id}/${y.name}\n"))
    }
  }

  def slackSend(channel: String, message: String) = Action.async {
    slackAPIActor ! SlackAPIActor.SendMessage(channel, message)
    Future(Ok("Attempted to Send Message"))
  }

  def startSM() = Action.async {
    serviceManagerActor ! Start
    Future(Ok("SM Start Attempted"))
  }

  def incident(incident: String) = Action.async {
    (serviceManagerActor ? ServiceManagerActor.Incident(incident)).mapTo[Option[IncidentDetail]].map {
      case None => NotFound("Incident Not Found")
      case Some(i: IncidentDetail) => Ok(s"${i.IncidentID}/${i.Description.foldLeft("")(_ + _.getOrElse("") + "\n")}")
    }
  }

  def incidentList(days: Int, priority: Int) = Action.async {
    (serviceManagerActor ? ServiceManagerActor.IncidentList(days, priority)).mapTo[Seq[String]].map { list =>
      Ok(s"${list.foldLeft("")(_ + _ + "\n")}")
    }
  }
}
