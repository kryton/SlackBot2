package modules

import javax.inject.{Inject, Named}

import actors.bots.IncidentActor
import actors.systems.ServiceManagerActor
import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext

/**
  * Created by iholsman on 12/7/2016.
  */
class IncidentModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[ServiceManagerActor]("ServiceManager-actor")
    bindActor[IncidentActor]("Incident-actor")
    bind(classOf[IncidentScheduler]).asEagerSingleton()
  }
}


class IncidentScheduler @Inject()(val system: ActorSystem, @Named("Incident-actor") val incidentActor: ActorRef)(implicit ec: ExecutionContext)
{
  system.scheduler.schedule(   0.microseconds, 1.minutes, incidentActor,IncidentActor.Tick)

  /*
  system.scheduler.schedule( 30.minutes, 30.days, incidentActor, "clean")
    */
}
