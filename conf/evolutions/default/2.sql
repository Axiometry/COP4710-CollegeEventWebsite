# --- Sample dataset

# --- !Ups

INSERT INTO University (id, name, full_name, location_coords, description, population) VALUES (0, 'UCF', 'University of Central Florida', '', '', 60000), (1, 'UF', 'University of Florida', '', '', 40000);

INSERT INTO Role (id, name) VALUES (0, 'Student'), (1, 'Admin'), (2, 'Super Admin');
INSERT INTO User (id, username, email, university_id, login_info, password_info, role_id) VALUES (0, 'admin', '', 0, '{"providerID":"credentials","providerKey":"0"}', NULL, 2);

INSERT INTO Rso VALUES (1, 'RSO1', 0, 0, 1),
                       (2, 'RSO2', 0, 0, 1),
                       (3, 'RSO3', 0, 0, 1);

INSERT INTO EventCategory (id, name) VALUES (0, 'Misc');

INSERT INTO EventVisibility (id, name) VALUES (0, 'Public'), (1, 'Private'), (2, 'RSO');

INSERT INTO Event VALUES (1, 'test1', 'asdfasdfasdf', 0, '2018-11-10 11:05:33', 'a', '40.748817,-73.585428', '123', 'a', 0, 1, 0, 1),
                         (2, 'test2', 'asdfasdfasdf', 0, '2018-11-10 09:05:33', 'a', '40.748817,-73.685428', '123', 'a', 0, 1, 0, 1),
                         (3, 'test3', 'asdfasdfasdf', 0, '2018-11-09 11:05:33', 'a', '40.748817,-73.785428', '123', 'a', 0, 1, 0, 2),
                         (4, 'test4', 'asdfasdfasdf', 0, '2018-11-20 11:07:33', 'a', '40.748817,-73.885428', '123', 'a', 0, 1, 0, 3),
                         (5, 'test5', 'asdfasdfasdf', 0, '2018-11-21 11:02:33', 'a', '40.748817,-73.985428', '123', 'a', 0, 1, 0, 3);

INSERT INTO EventComment VALUES (1, 1, 0, 5, 'This is comment 1'),
                                (2, 1, 0, 5, 'This is comment 2'),
                                (3, 1, 0, 5, 'This is comment 3');



# --- !Downs

DELETE FROM EventComment;
DELETE FROM Event;
DELETE FROM EventVisibility;
DELETE FROM EventCategory;
DELETE FROM Rso;
DELETE FROM User;
DELETE FROM Role;
DELETE FROM University;