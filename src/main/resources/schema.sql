DROP SCHEMA PUBLIC CASCADE;
COMMIT;

CREATE TABLE VALUESETS (
  ID INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  CODE VARCHAR(50) NOT NULL,
  DISPLAYNAME VARCHAR(10000)  NOT NULL,
  CODESYSTEMNAME VARCHAR(50)  NOT NULL,
  CODESYSTEMVERSION VARCHAR(50)  NOT NULL,
  CODESYSTEM VARCHAR(50)  NOT NULL,
  TTY VARCHAR(10)  NOT NULL,
  VALUESETNAME VARCHAR(75),
  VALUESETOID VARCHAR(50),
  VALUESETTYPE VARCHAR(50),
  VALUESETDEFINITIONVERSION VARCHAR(25),
  VALUESETSTEWARD VARCHAR(500)
);

CREATE TABLE CODES (
  ID INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  CODE VARCHAR(50) NOT NULL,
  DISPLAYNAME VARCHAR(5000) NOT NULL,
  CODESYSTEM VARCHAR(50) NOT NULL,
  CODESYSTEMOID VARCHAR(50) NOT NULL,
  ACTIVE BOOLEAN NOT NULL
);
COMMIT ;

CREATE INDEX IDX_VALUESETS ON VALUESETS (CODE, DISPLAYNAME, CODESYSTEMNAME, CODESYSTEM, VALUESETOID);
CREATE INDEX IDX_VALUESETOIDS ON VALUESETS (VALUESETOID);
CREATE INDEX IDX_CODESINCODESYSTEMANDINVALUESETOIDS ON VALUESETS (CODE, CODESYSTEM, VALUESETOID);
CREATE INDEX IDX_CODESYTEMS ON CODES (CODESYSTEM);
CREATE INDEX IDX_CODESYSTEMOIDS ON CODES (CODESYSTEMOID);
CREATE INDEX IDX_CODESINCODES ON CODES (CODE, DISPLAYNAME, CODESYSTEM, CODESYSTEMOID);
CREATE INDEX IDX_DISPLAYNAMEINCODES ON CODES (DISPLAYNAME, CODESYSTEM);
COMMIT ;
