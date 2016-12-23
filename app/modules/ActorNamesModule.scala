package modules

import actors.bots.{ ShoppingBotActor}
import actors.systems.{DRActor}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import slack.rtm.SlackAPIActor

class ActorNamesModule extends AbstractModule with AkkaGuiceSupport {

  def configure(): Unit = {
    //bindActor[SlackAPIActor]("SlackAPI-actor")
    bindActor[DRActor]("DR-actor")
   // bindActor[ShoppingBotActor]("ShoppingBot-actor")
  }
}
