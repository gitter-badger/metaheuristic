truncate table mh_exec_context;

alter table mh_exec_context
    add CTX_GRAPH_ID      NUMERIC(10, 0) default NULL;

alter table mh_exec_context
    add CTX_TASK_STATE_ID NUMERIC(10, 0) default NULL;

alter table mh_exec_context
    add CTX_VARIABLE_INFO_ID NUMERIC(10, 0) default NULL;

CREATE TABLE mh_exec_context_graph
(
    ID                SERIAL PRIMARY KEY,
    VERSION           NUMERIC(5, 0)  NOT NULL,
    EXEC_CONTEXT_ID   NUMERIC(10, 0) NOT NULL,
    PARAMS            TEXT NOT NULL
);

CREATE TABLE mh_exec_context_task_state
(
    ID                SERIAL PRIMARY KEY,
    VERSION           NUMERIC(5, 0)  NOT NULL,
    EXEC_CONTEXT_ID   NUMERIC(10, 0) NOT NULL,
    PARAMS            TEXT NOT NULL
);

CREATE TABLE mh_exec_context_variable_info
(
    ID                SERIAL PRIMARY KEY,
    VERSION           NUMERIC(5, 0)  NOT NULL,
    EXEC_CONTEXT_ID   NUMERIC(10, 0) NOT NULL,
    PARAMS            TEXT NOT NULL
);

