package models.services.mysql

import java.sql.SQLException

import scala.concurrent.Future

import anorm._
import anorm.SqlParser.{ get, str }
import javax.inject.Inject
import play.api.db.DBApi

import models._
import models.services._

@javax.inject.Singleton
class MysqlRsoService @Inject()(universityService: UniversityService, userService: UserService, dbapi: DBApi)(implicit ec: DatabaseExecutionContext) extends RsoService {
  import MysqlRsoService._
  
  private val db = dbapi.database("default")
  
  private type RsoRow = Long ~ String ~ Long ~ Long ~ Int
  private val rsoParser: RowParser[RsoRow] =
    get[Long]("Rso.id") ~
        get[String]("Rso.name") ~
        get[Long]("Rso.owner_id") ~
        get[Long]("Rso.university_id") ~
        get[Int]("Rso.approved")
  private val nextIdParser = get[Long]("next_id")
  
  private val rsoRowConverter: RsoRow => Future[Rso] = {
    case id ~ name ~ ownerId ~ universityId ~ approved =>
      universityService.retrieve(universityId).flatMap {
        case Some(university) =>
          userService.retrieve(ownerId).map {
            case Some(owner) => Rso(id, name, owner, university, approved == 1)
            case _ => throw new SQLException(s"Failed to find user $ownerId")
          }
        case _ => throw new SQLException(s"Failed to find university $universityId")
      }
  }
  private val seqRsoRowConverter: Seq[RsoRow] => Future[Seq[Rso]] = rows => Future.sequence(rows.map(rsoRowConverter))
  private val optRsoRowConverter: Option[RsoRow] => Future[Option[Rso]] = row => Future.sequence(row.toList.map(rsoRowConverter)).map(_.headOption)
  
  override def retrieve(): Future[Seq[Rso]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM Rso".as(rsoParser.*)
    }
  }.flatMap(seqRsoRowConverter)
  
  override def retrieve(id: Long): Future[Option[Rso]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM Rso WHERE Rso.id = $id".as(rsoParser.singleOpt)
    }
  }.flatMap(optRsoRowConverter)
  
  override def retrieve(owner: User): Future[Seq[Rso]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM Rso WHERE Rso.owner_id = ${owner.id}".as(rsoParser.*)
    }
  }.flatMap(seqRsoRowConverter)
  
  
  // TODO update+insert lol
  override def save(rso: Rso): Future[Rso] = {
    retrieve(rso.id).map { result =>
      db.withConnection { implicit connection =>
        val rowsUpdated = result match {
          case Some(_) => update(rso)
          case None    => insert(rso)
        }
        rowsUpdated match {
          case 0 => throw new SQLException(s"Failed to save RSO ${rso.id}")
          case _ => rso
        }
      }
    }
  }
  private def update(rso: Rso)(implicit connection: java.sql.Connection): Int = {
    SQL"""
      UPDATE Rso
      SET
        name = ${rso.name},
        owner_id = ${rso.owner.id},
        university_id = ${rso.university.id},
        approved = ${if(rso.approved) 1 else 0}
      WHERE id = ${rso.id}
    """.executeUpdate()
  }
  private def insert(rso: Rso)(implicit connection: java.sql.Connection): Int = {
    SQL"""
      INSERT INTO Rso
      VALUES (
        ${rso.id},
        ${rso.name},
        ${rso.owner.id},
        ${rso.university.id},
        ${if(rso.approved) 1 else 0}
      )
    """.executeInsert()
    1
  }
  
  override def delete(rso: Rso): Future[Unit] = Future {
    db.withConnection { implicit connection =>
      SQL"DELETE FROM Rso WHERE Rso.id = ${rso.id}".execute()
    }
  }
  
  override def nextId(): Future[Long] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT MAX(Rso.id)+1 AS next_id FROM Rso".as(nextIdParser.single)
    }
  }
}
object MysqlRsoService {

}