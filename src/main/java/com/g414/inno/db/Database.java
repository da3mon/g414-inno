package com.g414.inno.db;

import java.nio.LongBuffer;
import java.util.Map;

import com.g414.inno.db.TableBuilder.IndexPart;
import com.g414.inno.db.impl.Util;
import com.g414.inno.jna.impl.InnoDB;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

public class Database {
    public Database() {
        this(new DatabaseConfiguration());
    }

    public Database(DatabaseConfiguration c) {
        Util.assertSuccess(InnoDB.ib_init());

        if (c.isAdaptiveHashEnabled()) {
            Util
                    .assertSuccess(InnoDB
                            .ib_cfg_set_bool_on("adaptive_hash_index"));
        } else {
            Util.assertSuccess(InnoDB
                    .ib_cfg_set_bool_off("adaptive_hash_index"));
        }

        if (c.isAdaptiveFlushingEnabled()) {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_on("adaptive_flushing"));
        } else {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_off("adaptive_flushing"));
        }

        if (c.isDoublewriteEnabled()) {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_on("doublewrite"));
        } else {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_off("doublewrite"));
        }

        if (c.isFilePerTableEnabled()) {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_on("file_per_table"));
        } else {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_off("file_per_table"));
        }

        if (c.isPageChecksumsEnabled()) {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_on("checksums"));
        } else {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_off("checksums"));
        }

        if (c.isPrintVerboseLog()) {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_on("print_verbose_log"));
        } else {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_off("print_verbose_log"));
        }

        if (c.isRollbackOnTimeoutEnabled()) {
            Util
                    .assertSuccess(InnoDB
                            .ib_cfg_set_bool_on("rollback_on_timeout"));
        } else {
            Util.assertSuccess(InnoDB
                    .ib_cfg_set_bool_off("rollback_on_timeout"));
        }

        if (c.isStatusFileEnabled()) {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_on("status_file"));
        } else {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_off("status_file"));
        }

        if (c.isSysMallocEnabled()) {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_on("use_sys_malloc"));
        } else {
            Util.assertSuccess(InnoDB.ib_cfg_set_bool_off("use_sys_malloc"));
        }

        Util.assertSuccess(InnoDB.ib_cfg_set("data_file_path", c
                .getDatafilePath()));
        Util.assertSuccess(InnoDB.ib_cfg_set("data_home_dir", c
                .getDataHomeDir()));
        Util.assertSuccess(InnoDB.ib_cfg_set("log_group_home_dir", c
                .getLogFileHomeDirectory()));
        Util.assertSuccess(InnoDB.ib_cfg_set("flush_log_at_trx_commit", c
                .getFlushLogAtTrxCommitMode().getCode()));
        Util.assertSuccess(InnoDB.ib_cfg_set("flush_method", c.getFlushMethod()
                .getCode()));
        Util.assertSuccess(InnoDB.ib_cfg_set("force_recovery", c
                .getRecoveryMethod().getCode()));

        Util.assertSuccess(InnoDB.ib_cfg_set("additional_mem_pool_size", c
                .getAdditionalMemPoolSize()));
        Util.assertSuccess(InnoDB.ib_cfg_set("buffer_pool_size", c
                .getBufferPoolSize()));

        Util.assertSuccess(InnoDB.ib_cfg_set("lru_block_access_recency", c
                .getLruBlockAccessRecency()));
        Util.assertSuccess(InnoDB.ib_cfg_set("lru_old_blocks_pct", c
                .getLruOldBlocksPct()));
        Util.assertSuccess(InnoDB.ib_cfg_set("max_dirty_pages_pct", c
                .getMaxDirtyPagesPct()));
        Util.assertSuccess(InnoDB.ib_cfg_set("max_purge_lag", c
                .getMaxPurgeLagSeconds()));
        Util.assertSuccess(InnoDB.ib_cfg_set("open_files", c
                .getOpenFilesLimit()));
        Util.assertSuccess(InnoDB.ib_cfg_set("autoextend_increment", c
                .getAutoextendIncrementSizePages()));
        Util.assertSuccess(InnoDB.ib_cfg_set("file_io_threads", c
                .getFileIOThreads()));
        Util.assertSuccess(InnoDB.ib_cfg_set("read_io_threads", c
                .getReadIOThreads()));
        Util.assertSuccess(InnoDB.ib_cfg_set("io_capacity", c
                .getIoCapacityIOPS()));
        Util.assertSuccess(InnoDB.ib_cfg_set("sync_spin_loops", c
                .getSyncSpinLoops()));

        Util.assertSuccess(InnoDB.ib_cfg_set("write_io_threads", c
                .getWriteIOThreads()));

        Util.assertSuccess(InnoDB.ib_startup(c.getFileFormat().getCode()));
    }

    public void createDatabase(String databaseName) {
        Util.assertSchemaOperationSuccess(InnoDB
                .ib_database_create(databaseName));
    }

    public void dropDatabase(String databaseName) {
        Util.assertSuccess(InnoDB.ib_database_drop(databaseName));
    }

    public Transaction beginTransaction(Level level) {
        Pointer trx = InnoDB.ib_trx_begin(level.getCode());

        return new Transaction(trx);
    }

    public Schema createSchema(String name, TableType format, int pageSize) {
        PointerByReference schema = new PointerByReference();
        Transaction trx = this.beginTransaction(Level.REPEATABLE_READ);
        Util.assertSuccess(InnoDB.ib_schema_lock_exclusive(trx.getTrx()));
        Util.assertSuccess(InnoDB.ib_table_schema_create(name, schema, format
                .getCode(), pageSize));
        trx.commit();

        return new Schema(schema);
    }

    public void createTable(Schema schema, TableDef tableDef) {
        boolean found = tableExists(tableDef);

        if (found) {
            throw new InnoException("table already exists: "
                    + tableDef.getName());
        }

        for (ColumnDef def : tableDef.getColumnDefs().values()) {
            int attr = 0;
            for (ColumnAttribute a : def.getAttrs()) {
                attr |= a.getCode();
            }

            if (!def.getType().equals(ColumnType.BLOB)) {
                Util.assertSuccess(InnoDB
                        .ib_table_schema_add_col(schema.getSchema().getValue(),
                                def.getName(), def.getType().getCode(), attr,
                                (short) 0, def.getLength().intValue()));
            } else {
                Util.assertSuccess(InnoDB.ib_table_schema_add_col(schema
                        .getSchema().getValue(), def.getName(), def.getType()
                        .getCode(), 0, (short) 0, 0));
            }
        }

        for (Map.Entry<String, IndexDef> entry : tableDef.getIndexDefs()
                .entrySet()) {
            PointerByReference index = new PointerByReference();
            Util.assertSuccess(InnoDB.ib_table_schema_add_index(schema
                    .getSchema().getValue(), entry.getKey(), index));
            IndexDef part = entry.getValue();

            for (IndexPart col : part.getColumns()) {
                Util.assertSuccess(InnoDB.ib_index_schema_add_col(index
                        .getValue(), col.getColumn(), col.getPrefixLen()));
            }

            if (part.isClustered()) {
                Util.assertSuccess(InnoDB.ib_index_schema_set_clustered(index
                        .getValue()));
            } else if (part.isUnique()) {
                Util.assertSuccess(InnoDB.ib_index_schema_set_unique(index
                        .getValue()));
            }
        }

        // TODO: cleanup locking and transactions w/ finally
        Transaction trx = this.beginTransaction(Level.REPEATABLE_READ);
        LongBuffer tableId = LongBuffer.allocate(1);

        try {
            Util.assertSuccess(InnoDB.ib_schema_lock_exclusive(trx.getTrx()));
            Util.assertSuccess(InnoDB.ib_table_create(trx.getTrx(), schema
                    .getSchema().getValue(), tableId));
            trx.commit();
        } catch (InnoException e) {
            trx.rollback();

            throw e;
        } finally {
            // TODO - better handling of rollback/release
        }
    }

    public Long truncateTable(String tableName) {
        LongBuffer tableId = LongBuffer.allocate(1);
        Util.assertSuccess(InnoDB.ib_table_truncate(tableName, tableId));

        return tableId.get();
    }

    public boolean tableExists(TableDef tableDef) {
        boolean found = false;
        Transaction check = null;
        try {
            check = this.beginTransaction(Level.REPEATABLE_READ);
            check.openTable(tableDef);

            found = true;
        } catch (InnoException expected) {
            if (!expected.getMessage().contains("Table not found")) {
                throw expected;
            }
        } finally {
            if (check != null) {
                check.rollback();
            }
        }
        return found;
    }

    public void shutdown(boolean fast) {
        int flag = fast ? InnoDB.ib_shutdown_t.IB_SHUTDOWN_NO_BUFPOOL_FLUSH
                : InnoDB.ib_shutdown_t.IB_SHUTDOWN_NORMAL;
        Util.assertSuccess(InnoDB.ib_shutdown(flag));
    }
}
