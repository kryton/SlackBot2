package controllers

import javax.inject._

import actors.BotMessages.{End, GetConfig, Start}
import actors.systems.ServiceManagerActor
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json.JsValue
import play.api.mvc._
import slack.models.Channel
import slack.rtm.SlackAPIActor
import utils.IncidentDetail

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlackController @Inject()(@Named("SlackAPI-actor") slackAPIActor: ActorRef,
                                @Named("DR-actor") drActor: ActorRef,
                                @Named("ShoppingBot-actor") shoppingActor: ActorRef
                           )(implicit ec: ExecutionContext) extends Controller {

  def actionEndpoint = Action { request =>
    val body: AnyContent = request.body
    val jsonBody:Option[JsValue] = body.asJson
    jsonBody match {
      case Some(json:JsValue) =>
        println(json.toString())
        val challenge =  (json \ "challenge").as[String]
        Ok(challenge)
      case None =>
        BadRequest("Json Body not found")
    }
    Ok("")
  }

  implicit val timeout: Timeout = 5.seconds

}
