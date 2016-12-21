package models

import java.sql.Date
import javax.inject.Inject

import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by iholsman on 9/19/2016.
  */


case class SlackSession(
                      token: String,
                      scope: String)

class SlackSessionRepo  @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  val db = dbConfig.db

  import dbConfig.driver.api._

  val tableQuery = TableQuery[SlackSessionTable]
  class SlackSessionTable(tag: Tag) extends Table[SlackSession](tag, "SlackSession") {
    def token = column[String]("token")
    def scope = column[String]("scope")

    def * = (token,scope) <> ((SlackSession.apply _).tupled, SlackSession.unapply)
  }

  def truncate() = {
    db.run(tableQuery.delete)
  }


  def all():Future[List[SlackSession]] = {
    db.run(tableQuery.to[List].result)
  }


  def find(token: String): Future[Option[SlackSession]] = {
    db.run( tableQuery.filter(_.token === token).result.headOption)
  }



  /**
    * Delete a specific entity by id. If successfully completed return 1. otherwise 0
    */
  def delete(token: String): Future[Int] = db.run( tableQuery.filter(_.token === token ).delete )

  def create(token: String, scope: String): SlackSession = {
    val newObj = SlackSession(token=token, scope=scope)
    tableQuery += newObj

    newObj
  }
}
