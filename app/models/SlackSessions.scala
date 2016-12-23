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


case class SlackSession( teamId: String,                      token: String)

class SlackSessionRepo  @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  val db = dbConfig.db

  import dbConfig.driver.api._

  val tableQuery = TableQuery[SlackSessionTable]
  class SlackSessionTable(tag: Tag) extends Table[SlackSession](tag, "SLACKSESSION") {
    def teamId = column[String]("teamId")
    def token = column[String]("token")

    def * = (teamId, token) <> ((SlackSession.apply _).tupled, SlackSession.unapply)
  }

  def truncate() = {
    db.run(tableQuery.delete)
  }

  def all():Future[List[SlackSession]] = {
    db.run(tableQuery.to[List].result)
  }


  def findByToken(token: String): Future[Option[SlackSession]] = {
    db.run( tableQuery.filter(_.token === token).result.headOption)
  }
  def findByTeamId(id: String): Future[Option[SlackSession]] = {
    db.run( tableQuery.filter(_.teamId === id).result.headOption)
  }

  /**
    * Delete a specific entity by id. If successfully completed return 1. otherwise 0
    */
  def deleteByToken(token: String): Future[Int] = db.run( tableQuery.filter(_.token === token ).delete )
  def deleteByTeamId(id: String): Future[Int] = db.run( tableQuery.filter(_.teamId === id ).delete )

  def create(teamId:String, token: String): SlackSession = {
    val newObj = SlackSession(teamId = teamId, token=token)
    tableQuery += newObj
    newObj
  }
}
