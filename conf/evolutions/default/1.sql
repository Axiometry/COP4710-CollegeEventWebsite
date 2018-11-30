# --- First database schema

# --- !Ups

CREATE TABLE Role (
  id INT NOT NULL,
  name VARCHAR(255) NOT NULL,

  CONSTRAINT PK_Role PRIMARY KEY (id)
);

CREATE TABLE University (
  id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  location_coords VARCHAR(255) NOT NULL,
  description VARCHAR(4000) NOT NULL,
  population BIGINT NOT NULL,

  CONSTRAINT PK_University PRIMARY KEY (id)
);

CREATE TABLE User (
  id BIGINT NOT NULL,
  username VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  university_id BIGINT,
  login_info VARCHAR(2048) NOT NULL,
  password_info VARCHAR(2048),
  role_id INT NOT NULL,

  CONSTRAINT PK_User PRIMARY KEY (id),
  CONSTRAINT FK_User_university_id FOREIGN KEY (university_id) REFERENCES University(id) ON DELETE CASCADE,
  CONSTRAINT FK_User_role_id FOREIGN KEY (role_id) REFERENCES Role(id)
);

CREATE TABLE Rso (
  id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  owner_id BIGINT NOT NULL,
  university_id BIGINT NOT NULL,
  approved TINYINT NOT NULL,

  CONSTRAINT PK_Rso PRIMARY KEY (id),
  CONSTRAINT FK_Rso_owner_id FOREIGN KEY (owner_id) REFERENCES User(id) ON DELETE CASCADE,
  CONSTRAINT FK_Rso_university_id FOREIGN KEY (university_id) REFERENCES University(id) ON DELETE CASCADE
);

CREATE TABLE RsoMember (
  rso_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  approved TINYINT NOT NULL,

  CONSTRAINT PK_RsoMember PRIMARY KEY (rso_id, user_id),
  CONSTRAINT FK_RsoMember_rso_id FOREIGN KEY (rso_id) REFERENCES Rso(id) ON DELETE CASCADE,
  CONSTRAINT FK_RsoMember_user_id FOREIGN KEY (user_id) REFERENCES User(id) ON DELETE CASCADE
);

CREATE TABLE EventCategory (
  id INT NOT NULL,
  name VARCHAR(255) NOT NULL,

  CONSTRAINT PK_EventCategory PRIMARY KEY (id)
);
CREATE TABLE EventVisibility (
  id INT NOT NULL,
  name VARCHAR(255) NOT NULL,

  CONSTRAINT PK_EventVisibility PRIMARY KEY (id)
);
CREATE TABLE Event (
  id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(4000) NOT NULL,
  category_id INT NOT NULL DEFAULT 0,
  date_time DATETIME NOT NULL,
  location_name VARCHAR(255) NOT NULL,
  location_coords VARCHAR(255) NOT NULL,
  contact_phone VARCHAR(255) NOT NULL,
  contact_email VARCHAR(255) NOT NULL,
  visibility_id INT NOT NULL,
  approved TINYINT NOT NULL,

  owner_id BIGINT NOT NULL,
  rso_id BIGINT,

  CONSTRAINT PK_Event PRIMARY KEY (id),
  CONSTRAINT FK_Event_category_id FOREIGN KEY (category_id) REFERENCES EventCategory(id) ON DELETE SET DEFAULT,
  CONSTRAINT FK_Event_visibility_id FOREIGN KEY (visibility_id) REFERENCES EventVisibility(id),
  CONSTRAINT FK_Event_owner_id FOREIGN KEY (owner_id) REFERENCES User(id) ON DELETE CASCADE,
  CONSTRAINT FK_Event_rso_id FOREIGN KEY (rso_id) REFERENCES Rso(id) ON DELETE CASCADE
);

CREATE TABLE EventComment (
  id BIGINT NOT NULL,
  event_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  rating TINYINT,
  body TEXT NOT NULL,

  CONSTRAINT PK_EventComment PRIMARY KEY (id, event_id),
  CONSTRAINT FK_EventComment_event_id FOREIGN KEY (event_id) REFERENCES Event(id) ON DELETE CASCADE,
  CONSTRAINT FK_EventComment_user_id FOREIGN KEY (user_id) REFERENCES User(id) ON DELETE CASCADE
);


# --- !Downs


DROP TABLE IF EXISTS Role;
DROP TABLE IF EXISTS University;
DROP TABLE IF EXISTS User;
DROP TABLE IF EXISTS Rso;
DROP TABLE IF EXISTS RsoMember;
DROP TABLE IF EXISTS EventCategory;
DROP TABLE IF EXISTS EventVisibility;
DROP TABLE IF EXISTS Event;
DROP TABLE IF EXISTS EventComment;

