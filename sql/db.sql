create table AIAI_IDS (
SEQUENCE_NAME VARCHAR(50),
SEQUENCE_NEXT_VALUE DECIMAL(10)
)
/

CREATE UNIQUE INDEX AIAI_IDS_SEQUENCE_NAME_IDX ON AIAI_IDS (SEQUENCE_NAME)
/

CREATE UNIQUE INDEX AIAI_IDS_SEQUENCE_NAME_NEXT_VAL
ON AIAI_IDS
(SEQUENCE_NAME, SEQUENCE_NEXT_VALUE)
/

create table AIAI_STATION (
ID numeric(10,0) not null,
VERSION numeric(5,0) NOT NULL,
IP varchar(30),
DESCRIPTION varchar(250)
)
/

create table AIAI_DATASET (
ID numeric(10,0) not null,
VERSION numeric(5,0) NOT NULL,
DESCRIPTION varchar(250)
)
/
