create table MH_LAUNCHPAD_ADDRESS
(
  ID          bigserial not null PRIMARY KEY,
  VERSION     bigint NOT NULL,
  URL varchar(200) not null,
  DESCRIPTION varchar(100) not null,
  SIGNATURE varchar(1000)
);

create table MH_ACCOUNT
(
  ID  bigserial not null PRIMARY KEY,
  VERSION bigint NOT NULL,
  USERNAME varchar(30) not null,
  TOKEN varchar(50) not null,
  PASSWORD varchar(100) not null,
  ROLES varchar(100),
  PUBLIC_NAME varchar(100),

  is_acc_not_expired BOOLEAN not null default true,
  is_not_locked BOOLEAN not null default false,
  is_cred_not_expired BOOLEAN not null default false,
  is_enabled BOOLEAN not null default false,

  mail_address varchar(100) ,
  PHONE varchar(100) ,
  PHONE_AS_STR varchar(100) ,

  CREATED_ON bigint not null
);

CREATE TABLE MH_STATION (
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(10, 0)  NOT NULL,
  UPDATED_ON  bigint not null,
  IP          VARCHAR(30),
  DESCRIPTION VARCHAR(250),
  STATUS      TEXT NOT NULL
);

CREATE TABLE MH_LOG_DATA (
  ID          SERIAL PRIMARY KEY,
  REF_ID      NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  UPDATE_TS   TIMESTAMP DEFAULT to_timestamp(0),
  LOG_TYPE    NUMERIC(5, 0)  NOT NULL,
  LOG_DATA    TEXT not null
);

CREATE TABLE MH_DATA (
  ID          SERIAL PRIMARY KEY,
  CODE        VARCHAR(200) not null,
  POOL_CODE   VARCHAR(250) not null,
  DATA_TYPE   NUMERIC(2, 0) NOT NULL,
  VERSION     NUMERIC(5, 0) NOT NULL,
  REF_ID      NUMERIC(10, 0),
  REF_TYPE    VARCHAR(15),
  UPLOAD_TS   TIMESTAMP DEFAULT to_timestamp(0),
  DATA        OID,
  CHECKSUM    VARCHAR(2048),
  IS_VALID    BOOLEAN not null default false,
  IS_MANUAL   BOOLEAN not null default false,
  FILENAME    VARCHAR(150),
  PARAMS      TEXT not null
);

CREATE INDEX MH_DATA_DATA_TYPE_IDX
    ON MH_DATA (DATA_TYPE);

CREATE INDEX MH_DATA_REF_ID_REF_TYPE_IDX
    ON MH_DATA (REF_ID, REF_TYPE);

CREATE INDEX MH_DATA_REF_TYPE_IDX
    ON MH_DATA (REF_TYPE);

CREATE INDEX MH_DATA_POOL_CODE_ID_IDX
  ON MH_DATA (POOL_CODE);

CREATE UNIQUE INDEX MH_DATA_CODE_UNQ_IDX
  ON MH_DATA (CODE);

CREATE TABLE MH_EXPERIMENT (
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  WORKBOOK_ID  NUMERIC(10, 0),
  CODE        VARCHAR(50)   NOT NULL,
  PARAMS        TEXT not null
);

CREATE UNIQUE INDEX MH_EXPERIMENT_CODE_UNQ_IDX
  ON MH_EXPERIMENT (CODE);

CREATE TABLE MH_TASK (
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  PARAMS        TEXT not null,
  STATION_ID    NUMERIC(10, 0),
  ASSIGNED_ON   bigint,
  IS_COMPLETED  BOOLEAN default false not null ,
  COMPLETED_ON  bigint,
  SNIPPET_EXEC_RESULTS  TEXT,
  METRICS      TEXT,
  TASK_ORDER   smallint not null,
  WORKBOOK_ID          NUMERIC(10, 0)   NOT NULL,
  EXEC_STATE   smallint not null default 0,
  IS_RESULT_RECEIVED  BOOLEAN default false not null,
  RESULT_RESOURCE_SCHEDULED_ON  bigint NOT NULL default 0,
  PROCESS_TYPE smallint not null
);

CREATE INDEX MH_TASK_WORKBOOK_ID_IDX
    ON MH_TASK (WORKBOOK_ID);

CREATE INDEX MH_TASK_WORKBOOK_ID_TASK_ORDER_IDX
    ON MH_TASK (WORKBOOK_ID, TASK_ORDER);

CREATE TABLE MH_SNIPPET (
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  SNIPPET_CODE    VARCHAR(100)  not null,
  SNIPPET_TYPE      VARCHAR(50) not null,
  PARAMS         TEXT
);

CREATE UNIQUE INDEX MH_SNIPPET_UNQ_IDX
  ON MH_SNIPPET (SNIPPET_CODE);

CREATE TABLE MH_PLAN (
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  CODE      varchar(50)  NOT NULL,
  CREATED_ON    bigint NOT NULL,
  PARAMS        TEXT not null,
  IS_LOCKED      BOOLEAN not null default false,
  IS_VALID      BOOLEAN not null default false
);

CREATE UNIQUE INDEX MH_PLAN_CODE_UNQ_IDX
  ON MH_PLAN (CODE);

CREATE TABLE MH_WORKBOOK (
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  PLAN_ID       NUMERIC(10, 0) NOT NULL,
  CREATED_ON    bigint NOT NULL,
  COMPLETED_ON  bigint,
  INPUT_RESOURCE_PARAM  TEXT NOT NULL,
  PRODUCING_ORDER integer NOT NULL,
  IS_VALID      BOOLEAN not null default false,
  EXEC_STATE   smallint not null default 0
);

CREATE TABLE MH_ATLAS
(
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  NAME          VARCHAR(50)   NOT NULL,
  DESCRIPTION   VARCHAR(250)  NOT NULL,
  CODE          VARCHAR(50)   NOT NULL,
  CREATED_ON    bigint not null,
  EXPERIMENT    TEXT NOT NULL
);

CREATE TABLE MH_ATLAS_TASK
(
    ID          SERIAL PRIMARY KEY,
    VERSION     NUMERIC(5, 0)  NOT NULL,
    ATLAS_ID    NUMERIC(10, 0)   NOT NULL,
    TASK_ID     NUMERIC(10, 0)   NOT NULL,
    PARAMS      TEXT not null
);

CREATE INDEX MH_ATLAS_TASK_ATLAS_ID_IDX
    ON MH_ATLAS_TASK (ATLAS_ID);

CREATE INDEX MH_ATLAS_TASK_ATLAS_ID_TASK_ID_IDX
    ON MH_ATLAS_TASK (ATLAS_ID, TASK_ID);

create table MH_BATCH
(
  ID               SERIAL PRIMARY KEY,
  VERSION          NUMERIC(5, 0)  NOT NULL,
  PLAN_ID          NUMERIC(10, 0) NOT NULL,
  DATA_ID          NUMERIC(10, 0),
  CREATED_ON       bigint         NOT NULL,
  EXEC_STATE      smallint not null default 0,
  PARAMS           TEXT
);

CREATE TABLE MH_BATCH_WORKBOOK
(
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  BATCH_ID    NUMERIC(10, 0) NOT NULL,
  WORKBOOK_ID NUMERIC(10, 0) NOT NULL
);

CREATE INDEX MH_BATCH_WORKBOOK_BATCH_ID_IDX
    ON MH_BATCH_WORKBOOK (BATCH_ID);

CREATE TABLE MH_EVENT
(
    ID              SERIAL PRIMARY KEY,
    VERSION         NUMERIC(5, 0)  NOT NULL,
    CREATED_ON      bigint         NOT NULL,
    EVENT           VARCHAR(50)    not null,
    PARAMS          TEXT     not null
);
