# Session SCHEMA

# --- !Ups
CREATE TABLE SLACKSESSION (
  teamID VARCHAR(20)  NOT NULL,
  token  VARCHAR(255) NOT NULL,
  PRIMARY KEY (token)
)
  # --- !Downs
DROP TABLE SLACKSESSION;
