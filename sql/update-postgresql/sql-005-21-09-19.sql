alter table MH_EXEC_CONTEXT
    add ROOT_EXEC_CONTEXT_ID   NUMERIC(10, 0);

CREATE INDEX MH_EXEC_CONTEXT_ROOT_EXEC_CONTEXT_ID_IDX
    ON MH_EXEC_CONTEXT (ROOT_EXEC_CONTEXT_ID);

