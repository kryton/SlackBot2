package modules
import javax.inject.{Inject, Named}

import actors.BotMessages.Start
import actors.bots.IncidentActor
import actors.systems.{DRActor, ServiceManagerActor, SlackAPIActor}
import actors.bots.ShoppingBotActor
import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.AbstractModule
import play.api.ApplicationLoader.Context
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.concurrent.Akka

import scala.concurrent.ExecutionContext

class ActorNamesModule  extends AbstractModule with AkkaGuiceSupport {
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def configure(): Unit = {
    bindActor[ServiceManagerActor]("ServiceManager-actor")
    bindActor[SlackAPIActor]("SlackAPI-actor")
    bindActor[IncidentActor]("Incident-actor")
    bindActor[DRActor]("DR-actor")
    bindActor[ShoppingBotActor]("ShoppingBot-actor")
 //   bind( classOf[Startup]).asEagerSingleton

/*
    val drActor = system.actorOf(DRActor.props(configuration),"system_DRActor")
    val slackActor = system.actorOf(SlackAPIActor.props(configuration),"system_SlackActor")
    val smActor = system.actorOf(ServiceManagerActor.props(configuration),"system_SMActor")
    val incidentActor = system.actorOf(IncidentActor.props(slackActor,smActor,configuration),"bot_IncidentActor")
    val shoppingBotActor = system.actorOf(ShoppingBotActor.props(slackActor,drActor,configuration),"bot_ShoppingBot")
*/
  }
}
/*
class Startup @Inject() ( val system: ActorSystem, @Named("SlackAPI-actor") slackAPIActor: ActorRef,
                          @Named("ServiceManager-actor") serviceManagerActor: ActorRef,
                          @Named("Incident-actor") incidentActor: ActorRef,
                          @Named("DR-actor") drActor: ActorRef,
                          @Named("ShoppingBot-actor") shoppingActor: ActorRef)(implicit ec:ExecutionContext) {
  drActor ! Start
  Logger.logger.info("App Started")

}
*/
/*
class CustomApplicationLoader extends GuiceApplicationLoader {
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  override def builder(context: Context): GuiceApplicationBuilder = {
    //val classLoader = context.environment.classLoader
    val result = initialBuilder.in(context.environment)
    //super.builder(context)
    val system =  ActorSystem()
    val configuration = context.initialConfiguration
    val drActor = system.actorOf(DRActor.props(configuration),"system_DRActor")
    val slackActor = system.actorOf(SlackAPIActor.props(configuration),"system_SlackActor")
    val smActor = system.actorOf(ServiceManagerActor.props(configuration),"system_SMActor")
    val incidentActor = system.actorOf(IncidentActor.props(slackActor,smActor,configuration),"bot_IncidentActor")
    val shoppingBotActor = system.actorOf(ShoppingBotActor.props(slackActor,drActor,configuration),"bot_ShoppingBot")
    result
  }
}
*/
