package models.services.mysql

import java.sql.SQLException

import scala.concurrent.Future

import anorm._
import anorm.SqlParser.get
import javax.inject.Inject
import play.api.db.DBApi

import models._
import models.services._

@javax.inject.Singleton
class MysqlRsoMembershipService @Inject()(rsoService: RsoService, userService: UserService, dbapi: DBApi)(implicit ec: DatabaseExecutionContext) extends RsoMembershipService {
  
  private val db = dbapi.database("default")
  
  private type RsoMemberRow = Long ~ Long ~ Int
  private val rsoMemberParser =
    get[Long]("RsoMember.rso_id") ~
        get[Long]("RsoMember.user_id") ~
        get[Int]("RsoMember.approved")
  
  private val rsoMemberRowConverter: RsoMemberRow => Future[RsoMembership] = {
    case rsoId ~ userId ~ approved =>
      rsoService.retrieve(rsoId).flatMap {
        case Some(rso) =>
          userService.retrieve(userId).map {
            case Some(user) => RsoMembership(rso, user, approved == 1)
            case _ => throw new SQLException(s"Failed to find user $userId")
          }
        case _ => throw new SQLException(s"Failed to find RSO $rsoId")
      }
  }
  private val seqRsoMemberRowConverter: Seq[RsoMemberRow] => Future[Seq[RsoMembership]] = rows => Future.sequence(rows.map(rsoMemberRowConverter))
  private val optRsoMemberRowConverter: Option[RsoMemberRow] => Future[Option[RsoMembership]] = row => Future.sequence(row.toList.map(rsoMemberRowConverter)).map(_.headOption)
  
  
  override def retrieve(rso: Rso): Future[Seq[RsoMembership]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM RsoMember WHERE RsoMember.rso_id = ${rso.id}".as(rsoMemberParser.*)
    }
  }.flatMap(seqRsoMemberRowConverter)
  
  override def retrieve(user: User): Future[Seq[RsoMembership]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM RsoMember WHERE RsoMember.user_id = ${user.id}".as(rsoMemberParser.*)
    }
  }.flatMap(seqRsoMemberRowConverter)
  
  override def retrieve(rso: Rso, user: User): Future[Option[RsoMembership]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM RsoMember WHERE RsoMember.rso_id = ${rso.id} AND RsoMember.user_id = ${user.id}".as(rsoMemberParser.singleOpt)
    }
  }.flatMap(optRsoMemberRowConverter)
  
  
  // TODO update+insert lol
  override def save(membership: RsoMembership): Future[RsoMembership] = {
    retrieve(membership.rso, membership.user).map { result =>
      db.withConnection { implicit connection =>
        val rowsUpdated = result match {
          case Some(_) => update(membership)
          case None    => insert(membership)
        }
        rowsUpdated match {
          case 0 => throw new SQLException(s"Failed to save RSO membership ${membership.rso.id} -> ${membership.user.id}")
          case _ => membership
        }
      }
    }
  }
  private def update(membership: RsoMembership)(implicit connection: java.sql.Connection): Int = {
    SQL"""
      UPDATE RsoMember
      SET
        approved = ${if(membership.approved) 1 else 0}
      WHERE rso_id = ${membership.rso.id} AND user_id = ${membership.user.id}
    """.executeUpdate()
  }
  private def insert(membership: RsoMembership)(implicit connection: java.sql.Connection): Int = {
    SQL"""
      INSERT INTO RsoMember
      VALUES (
        ${membership.rso.id},
        ${membership.user.id},
        ${if(membership.approved) 1 else 0}
      )
    """.executeInsert()
    1
  }
  
  override def delete(membership: RsoMembership): Future[Unit] = Future {
    db.withConnection { implicit connection =>
      SQL"DELETE FROM RsoMember WHERE RsoMember.rso_id = ${membership.rso.id} AND RsoMember.user_id = ${membership.user.id}".execute()
    }
  }
}
