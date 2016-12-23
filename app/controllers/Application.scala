package controllers

import javax.inject._

import actors.BotMessages.{End, GetConfig, Start}
import actors.systems.ServiceManagerActor
import akka.actor.ActorRef
import akka.http.scaladsl.model.DateTime
import akka.pattern.ask
import akka.util.Timeout
import play.api.Configuration
import play.api.mvc._
import slack.models.Channel
import slack.rtm.SlackAPIActor
import utils.{IncidentDetail, ServiceManagerAPI}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Application @Inject()(configuration: Configuration,
                            @Named("ServiceManager-actor") serviceManagerActor: ActorRef,
                            @Named("Incident-actor") incidentActor: ActorRef
                           )(implicit ec: ExecutionContext) extends Controller {

  def index = Action.async {
    val clientId: String = configuration.getString("slack.config.client.ID").getOrElse("none")

    Future(Ok(views.html.index("Click the button", clientId)))
  }

  implicit val timeout: Timeout = 5.seconds

  def IMstart: Action[AnyContent] = Action.async {
    incidentActor ! Start
    Future(Ok("Attempted to start IM Bot"))
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
