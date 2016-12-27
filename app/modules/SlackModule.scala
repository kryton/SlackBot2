package modules

import javax.inject.{Inject, Named}

import actors.systems.SlackTeamManager
import play.api.libs.concurrent.AkkaGuiceSupport
import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.AbstractModule
import models.SlackSessionRepo
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext

/**
  * Created by iholsman on 12/21/2016.
  */
class SlackModule extends AbstractModule  with AkkaGuiceSupport {
  def configure() = {
    bindActor[SlackTeamManager]("SlackTeamManager-actor")
    bind(classOf[SlackTeamManagerLoader]).asEagerSingleton()
  }
}

class SlackTeamManagerLoader @Inject()(val system: ActorSystem,
                                       @Named("SlackTeamManager-actor") val teamManagerActor: ActorRef,
                                       @Named("DR-actor") val drActor: ActorRef,
                                       slackSessionRepo: SlackSessionRepo)(implicit ec: ExecutionContext)
{
  val logger = Logger.logger
  logger.warn("Slack Startup")
  //slackSessionRepo.createIt()

  teamManagerActor ! SlackTeamManager.Load(slackSessionRepo,drActor)

}
