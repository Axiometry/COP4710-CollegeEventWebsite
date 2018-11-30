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
class MysqlUniversityService @Inject()(dbapi: DBApi)(implicit ec: DatabaseExecutionContext) extends UniversityService {
  
  private val db = dbapi.database("default")
  
  private val universityParser = {
    get[Long]("University.id") ~
        get[String]("University.name") ~
        get[String]("University.full_name") ~
        get[String]("University.location_coords") ~
        get[String]("University.description") ~
        get[Long]("University.population") map {
      case id ~ name ~ fullName ~ locationCoords ~ description ~ population =>
        University(id, name, fullName, locationCoords, description, population)
    }
  }
  private val nextIdParser = get[Long]("next_id")
  
  override def retrieve(): Future[Seq[University]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM University".as(universityParser.*)
    }
  }
  
  override def retrieve(id: Long): Future[Option[University]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM University WHERE University.id = $id".as(universityParser.singleOpt)
    }
  }
  
  
  // TODO update+insert lol
  override def save(university: University): Future[University] = {
    retrieve(university.id).map { result =>
      db.withConnection { implicit connection =>
        val rowsUpdated = result match {
          case Some(_) => update(university)
          case None    => insert(university)
        }
        rowsUpdated match {
          case 0 => throw new SQLException(s"Failed to save university ${university.id}")
          case _ => university
        }
      }
    }
  }
  private def update(university: University)(implicit connection: java.sql.Connection): Int = {
    SQL"""
      UPDATE University
      SET
        name = ${university.name},
        full_name = ${university.fullName},
        location_coords = ${university.locationCoords},
        description = ${university.description},
        population = ${university.population}
      WHERE id = ${university.id}
    """.executeUpdate()
  }
  private def insert(university: University)(implicit connection: java.sql.Connection): Int = {
    SQL"""
      INSERT INTO University
      VALUES (
        ${university.id},
        ${university.name},
        ${university.fullName},
        ${university.locationCoords},
        ${university.description},
        ${university.population}
      )
    """.executeInsert()
    1
  }
  
  override def delete(university: University): Future[Unit] = Future {
    db.withConnection { implicit connection =>
      SQL"DELETE FROM University WHERE University.id = ${university.id}".execute()
    }
  }
  
  override def nextId(): Future[Long] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT MAX(University.id)+1 AS next_id FROM University".as(nextIdParser.single)
    }
  }
}
