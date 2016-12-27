package controllers

import javax.inject._

import actors.BotMessages.{End, GetConfig, Start}
import actors.systems.{ SlackTeamManager}
import actors.systems.SlackTeamManager.SlackTeamMessage

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import models.SlackSessionRepo
import play.api.{Configuration, Logger}
import play.api.libs.json.JsValue
import play.api.mvc._
import slack.api.{AccessToken, SlackApiClient}
import slack.rtm.SlackAPIActor
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait JsonSlackResponseMarshallers extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val actionKeyPairFmt = jsonFormat2(SlackTeamManager.ActionKeyPair)
  implicit val teamFmt = jsonFormat2(SlackTeamManager.Team)
  implicit val channelFmt = jsonFormat2(SlackTeamManager.SlackChannel)
  implicit val userFmt = jsonFormat2(SlackTeamManager.SlackUser)
  implicit val payloadFmt = jsonFormat10(SlackTeamManager.Payload)
}


object JsonSlackResponseMarshallers extends JsonSlackResponseMarshallers

@Singleton
class SlackController @Inject()(configuration: Configuration,
                                @Named("SlackTeamManager-actor") slackTeamManager: ActorRef,
                                @Named("DR-actor") drActor: ActorRef,
                                slackSessionRepo: SlackSessionRepo
                           )(implicit ec: ExecutionContext, system:ActorSystem) extends Controller {

  implicit val timeout: Timeout = 5.seconds
  implicit val materializer = ActorMaterializer()
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
  import  JsonSlackResponseMarshallers._

  val logger = play.api.Logger.logger
  def actionEndpoint = Action.async { request =>

    request.contentType match {
      case Some(ct) =>
        val body: AnyContent = request.body
       // logger.info(s"CT=$ct - Action Endpoint ${request.uri} - $body")
        ct match {
          case "application/json" =>
            val jsonBody:Option[JsValue] = body.asJson

            jsonBody match {
              case Some(json:JsValue) =>
                println(json.toString())
                val challenge =  (json \ "challenge").as[String]
                Future(Ok(challenge))
              case None =>
                Future(BadRequest("Json Body not found"))
            }
          case "application/x-www-form-urlencoded" =>
            request.body.asFormUrlEncoded match {
              case Some(values) =>
                values.get("payload") match {
                  case Some(payloadValues) =>
                    payloadValues.headOption match {
                      case Some(payloadS) =>

                        val result: Future[SlackTeamManager.Payload] = Unmarshal[String](payloadS).to[SlackTeamManager.Payload]

                        result.map { payload:SlackTeamManager.Payload =>
                          val validation: String = configuration.getString("slack.config.client.token").getOrElse("none")
                          if ( validation != payload.token) {
                            BadRequest("Invalid Payload. Token is not what I expected")
                          } else {
                            Ok("{\n  \"response_type\": \"ephemeral\",\n  \"replace_original\": false,\n  \"text\": \"Received the Test Callback.\"\n}").as("application/json")
                          }
                        } recover {
                          case fail =>
                            logger.error(s"Attempting to Unpack Json - ${fail.getMessage}")
                            BadRequest("Invalid Payload. Can't unpack")
                        }

                      case None =>  Future(BadRequest("Missing Payload value"))
                    }
                  case None => Future(BadRequest("Missing Payload"))
                }
               // BadRequest("TBD")
              case None => Future(BadRequest("No parameters found (expecting payload)"))
            }

          case _ => Future(BadRequest(s"Unknown content type $ct"))
        }

      case None => Future(BadRequest (" Missing Content Type"))
    }

  }

  def actionRedirect(code:Option[String], state:Option[String]): Action[AnyContent] = Action.async { request =>

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
        Ok( views.html.slackDebug.getChannels( message))
       // Ok(message.foldLeft("")((x: String, y: Channel) => x + s"${y.id}/${y.name}\n"))
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
      slackSessionRepo.all().map { sessions:List[models.SlackSession] =>
        Ok( views.html.slackDebug.openSessions( teams,sessions))
      }

    }.flatMap(identity)
  }

  def getSlackActor( teamId:String): Future[ActorRef] =  {
    Logger.logger.error("WARNING - This call should only be used when in debug mode")
    (slackTeamManager?SlackTeamManager.GetSlackByTeam(teamId)).mapTo[ActorRef]
  }

}

