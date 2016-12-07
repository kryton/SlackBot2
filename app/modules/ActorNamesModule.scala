package modules

import actors.bots.{IncidentActor, ShoppingBotActor}
import actors.systems.{DRActor, ServiceManagerActor}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import slack.rtm.SlackAPIActor

class ActorNamesModule extends AbstractModule with AkkaGuiceSupport {

  def configure(): Unit = {
    bindActor[ServiceManagerActor]("ServiceManager-actor")
    bindActor[SlackAPIActor]("SlackAPI-actor")
    bindActor[IncidentActor]("Incident-actor")
    bindActor[DRActor]("DR-actor")
    bindActor[ShoppingBotActor]("ShoppingBot-actor")

  }
}
