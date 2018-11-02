CREATE TABLE AIAI_LP_STATION (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  IP          VARCHAR(30),
  UPDATE_TS   TIMESTAMP DEFAULT 0 ON UPDATE CURRENT_TIMESTAMP,
  DESCRIPTION VARCHAR(250),
  ENV       MEDIUMTEXT
);

CREATE TABLE AIAI_LOG_DATA (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  REF_ID      NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  UPDATE_TS   TIMESTAMP DEFAULT 0 ON UPDATE CURRENT_TIMESTAMP,
  LOG_TYPE    NUMERIC(5, 0)  NOT NULL,
  LOG_DATA    MEDIUMTEXT not null
);

CREATE TABLE AIAI_LP_DATA (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  REF_ID      NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  UPDATE_TS   TIMESTAMP DEFAULT 0 ON UPDATE CURRENT_TIMESTAMP,
  DATA_TYPE   NUMERIC(5, 0)  NOT NULL,
  DATA        BLOB,
  POOL_CODE   VARCHAR(250)
);

CREATE TABLE AIAI_LP_DATASET (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME        VARCHAR(40)  NOT NULL,
  DESCRIPTION VARCHAR(250) NOT NULL,
  IS_EDITABLE   tinyint(1) not null default 1,
  ASSEMBLY_SNIPPET_ID  NUMERIC(10, 0),
  DATASET_SNIPPET_ID  NUMERIC(10, 0),
  IS_LOCKED   tinyint(1) not null default 0,
  RAW_ASSEMBLING_STATUS   tinyint(1) not null default 0,
  DATASET_PRODUCING_STATUS   tinyint(1) not null default 0,
  DATASET_LENGTH NUMERIC(10, 0)
);

CREATE TABLE AIAI_LP_FEATURE (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  DATASET_ID  NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  FEATURE_ORDER  NUMERIC(3, 0) NOT NULL,
  DESCRIPTION VARCHAR(250),
  SNIPPET_ID  NUMERIC(10, 0),
  IS_REQUIRED tinyint(1) not null default 0,
  FEATURE_FILE         VARCHAR(250),
  STATUS     tinyint(1) not null default 0,
  FEATURE_LENGTH NUMERIC(10, 0)
);

CREATE TABLE AIAI_LP_EXPERIMENT (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  DATASET_ID  NUMERIC(10, 0),
  NAME        VARCHAR(50)   NOT NULL,
  DESCRIPTION VARCHAR(250)  NOT NULL,
  EPOCH       VARCHAR(100)  NOT NULL,
  EPOCH_VARIANT tinyint(1)  NOT NULL,
  SEED          INT(10),
  NUMBER_OF_SEQUENCE          INT(10) not null default 0,
  IS_ALL_SEQUENCE_PRODUCED   tinyint(1) not null default 0,
  IS_FEATURE_PRODUCED   tinyint(1) not null default 0,
  IS_LAUNCHED   tinyint(1) not null default 0,
  EXEC_STATE        tinyint(1) not null default 0,
  CREATED_ON   bigint not null,
  LAUNCHED_ON   bigint
);

CREATE INDEX AIAI_LP_EXPERIMENT_IS_LAUNCHED_EXEC_STATE_IDX
  ON AIAI_LP_EXPERIMENT (IS_LAUNCHED, EXEC_STATE);

CREATE TABLE AIAI_LP_EXPERIMENT_HYPER_PARAMS (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  EXPERIMENT_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  HYPER_PARAM_KEY    VARCHAR(50),
  HYPER_PARAM_VALUES  VARCHAR(250)
);

CREATE TABLE AIAI_LP_EXPERIMENT_FEATURE (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  EXPERIMENT_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  FEATURE_IDS   VARCHAR(512) not null,
  IS_IN_PROGRESS    tinyint(1) not null default 0,
  IS_FINISHED   tinyint(1) not null default 0
  EXEC_STATUS  tinyint(1) not null default 0
);

CREATE UNIQUE INDEX AIAI_LP_EXPERIMENT_FEATURE_UNQ_IDX
  ON AIAI_LP_EXPERIMENT_FEATURE (EXPERIMENT_ID, FEATURE_IDS);

CREATE TABLE AIAI_LP_TASK_SNIPPET (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  REF_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  SNIPPET_CODE   VARCHAR(100) NOT NULL,
  SNIPPET_TYPE   VARCHAR(20) not null,
  SNIPPET_ORDER  NUMERIC(3, 0) NOT NULL  default 0,
  TASK_TYPE  smallint not null
);

CREATE UNIQUE INDEX AIAI_LP_TASK_SNIPPET_UNQ_IDX
  ON AIAI_LP_TASK_SNIPPET (REF_ID, SNIPPET_ORDER);

CREATE INDEX AIAI_LP_TASK_SNIPPET_REF_ID_TASK_TYPE_IDX
  ON AIAI_LP_TASK_SNIPPET (REF_ID, TASK_TYPE);

CREATE TABLE AIAI_LP_EXPERIMENT_SEQUENCE (
  ID            INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  EXPERIMENT_ID          NUMERIC(10, 0) NOT NULL,
  FEATURE_ID          NUMERIC(10, 0) NOT NULL,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  PARAMS          MEDIUMTEXT not null,
  STATION_ID          NUMERIC(10, 0),
  ASSIGNED_ON   bigint,
  IS_COMPLETED  tinyint(1) not null default 0,
  COMPLETED_ON   bigint,
  SNIPPET_EXEC_RESULTS  MEDIUMTEXT,
  METRICS      MEDIUMTEXT,
  IS_ALL_SNIPPETS_OK  tinyint(1) not null default 0
);

CREATE TABLE AIAI_LP_SNIPPET (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME      VARCHAR(50) not null,
  SNIPPET_TYPE      VARCHAR(50) not null,
  SNIPPET_VERSION   VARCHAR(20) not null,
  FILENAME  VARCHAR(250) not null,
  CHECKSUM    VARCHAR(2048),
  IS_SIGNED   tinyint(1) not null default 0,
  IS_REPORT_METRICS   tinyint(1) not null default 0,
  ENV         VARCHAR(50) not null,
  PARAMS         VARCHAR(1000),
  CODE_LENGTH integer not null
);

CREATE UNIQUE INDEX AIAI_LP_SNIPPET_UNQ_IDX
  ON AIAI_LP_SNIPPET (NAME, SNIPPET_VERSION);

CREATE TABLE AIAI_LP_DATASET_PATH (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  DATASET_ID  NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  PATH_NUMBER NUMERIC(3, 0) NOT NULL,
  PATH        VARCHAR(200),
  REG_TS      TIMESTAMP NOT NULL,
  CHECKSUM    VARCHAR(2048),
  IS_FILE     tinyint(1) not null default 1,
  IS_VALID    tinyint(1) not null default 0
);

CREATE TABLE AIAI_LP_ENV (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  ENV_KEY     VARCHAR(50)  NOT NULL,
  ENV_VALUE   VARCHAR(500)  NOT NULL,
  SIGNATURE   varchar(1000)
);

CREATE UNIQUE INDEX AIAI_LP_ENVS_UNQ_IDX
  ON AIAI_LP_ENVS (ENV_KEY);
