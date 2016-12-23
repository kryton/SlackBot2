package controllers

import javax.inject._

import actors.BotMessages.{End, GetConfig, Start}
import actors.systems.{ServiceManagerActor, SlackTeamManager}
import actors.systems.SlackTeamManager.SlackTeamMessage
import scala.util.{Failure, Success}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import play.api.{Configuration, Logger}
import play.api.libs.json.JsValue
import play.api.mvc._
import slack.api.{AccessToken, SlackApiClient}
import slack.models.Channel
import slack.rtm.SlackAPIActor
import utils.IncidentDetail

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlackController @Inject()(configuration: Configuration,
                                @Named("SlackTeamManager-actor") slackTeamManager: ActorRef,
                                @Named("DR-actor") drActor: ActorRef
                           )(implicit ec: ExecutionContext, system:ActorSystem) extends Controller {

  implicit val timeout: Timeout = 5.seconds

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
  }

  def actionRedirect(code:Option[String], state:Option[String]) = Action.async { request =>

   code match {
     case Some(codeS) =>
       val clientId: String = configuration.getString("slack.config.client.ID").getOrElse("none")
       val clientSecret: String = configuration.getString("slack.config.client.secret").getOrElse("none")
       Logger.logger.info(s"actionRedirect = $codeS - clientId = $clientId - secret = $clientSecret")
       val tokenF:Future[AccessToken] = SlackApiClient.exchangeOauthForToken(clientId, clientSecret, codeS, None)

       tokenF.map { token: AccessToken =>
         slackTeamManager ! SlackTeamManager.ConnectShopperToSlack(token.access_token, drActor)
         Logger.logger.warn(s"RCVD Token - ${token.access_token}/${token.scope}")
         Ok(s"TBD - Redirect somewhere Token - ${token.access_token}/${token.scope} ")
       }.recover {
         case fail =>
         Logger.logger.error("OauthToken failed",fail)
         BadRequest(fail.getMessage)

       }
     case None =>
       println("XXXX Bad Call")
      Future( BadRequest("I need a code"))
   }
  }


  def slackGetConfig(teamId:String) = Action.async {
    getSlackActor(teamId).map { slackAPIActor =>
      (slackAPIActor ? GetConfig).mapTo[String].map { message =>
        Ok(views.html.slackDebug.getConfig(message))
      }
    }.flatMap(identity)
  }

  def slackChannels(teamId:String) = Action.async {
    getSlackActor(teamId).map { slackAPIActor =>
      (slackAPIActor ? SlackAPIActor.Channels).mapTo[Seq[slack.models.Channel]].map { message =>
        Ok(message.foldLeft("")((x: String, y: Channel) => x + s"${y.id}/${y.name}\n"))
      }
    }.flatMap(identity)
  }

  def slackUsers(teamId:String) = Action.async {
    getSlackActor(teamId).map { slackAPIActor =>
      (slackAPIActor ? SlackAPIActor.Users).mapTo[Seq[slack.models.User]].map { message =>
        Ok( views.html.slackDebug.getUsers( message))
        //Ok(message.foldLeft("")((x: String, y: slack.models.User) => x + s"${y.id}/${y.name}\n"))
      }
    }.flatMap(identity)
  }

  def slackSend(teamId:String,channel: String, message: String) = Action.async {
    getSlackActor(teamId).map { slackAPIActor =>
      slackAPIActor ! SlackAPIActor.SendMessage(channel, message)
      Ok("Attempted to Send Message")
    }
  }
  def slackList() = Action.async{
    (slackTeamManager ? SlackTeamManager.OpenSessions).mapTo[Seq[String]].map{ teams =>
      Ok( views.html.slackDebug.openSessions( teams))
    }
  }

  def getSlackActor( teamId:String): Future[ActorRef] =  {
    Logger.logger.error("WARNING - This call should only be used when in debug mode")
    (slackTeamManager?SlackTeamManager.GetSlackByTeam(teamId)).mapTo[ActorRef]
  }

}
