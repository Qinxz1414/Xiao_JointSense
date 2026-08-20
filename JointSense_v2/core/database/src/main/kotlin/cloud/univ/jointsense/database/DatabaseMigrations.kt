package cloud.univ.jointsense.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `measurement_batch` (
                    `id` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `draftId` TEXT NOT NULL,
                    `measuredAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `test_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_batch_sessionId` ON `measurement_batch` (`sessionId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_batch_draftId` ON `measurement_batch` (`draftId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `test_result_v2` (
                    `id` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `draftId` TEXT,
                    `factor` TEXT NOT NULL,
                    `concentration` REAL NOT NULL,
                    `rangeStatus` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `rMean` REAL NOT NULL,
                    `gMean` REAL NOT NULL,
                    `bMean` REAL NOT NULL,
                    `rStd` REAL NOT NULL,
                    `gStd` REAL NOT NULL,
                    `bStd` REAL NOT NULL,
                    `measurementBatchId` TEXT,
                    `rawSignal` REAL NOT NULL,
                    `signalMethod` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `test_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`measurementBatchId`) REFERENCES `measurement_batch`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `test_result_v2` (
                    `id`, `sessionId`, `draftId`, `factor`, `concentration`, `rangeStatus`, `timestamp`,
                    `rMean`, `gMean`, `bMean`, `rStd`, `gStd`, `bStd`, `measurementBatchId`, `rawSignal`,
                    `signalMethod`
                )
                SELECT `id`, `sessionId`, `draftId`, `factor`, `concentration`, `rangeStatus`, `timestamp`,
                    `rMean`, `gMean`, `bMean`, `rStd`, `gStd`, `bStd`, NULL, (`bMean` - `rMean`),
                    'LEGACY_MEAN_BR'
                FROM `test_result`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `test_result`")
            db.execSQL("ALTER TABLE `test_result_v2` RENAME TO `test_result`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_test_result_sessionId` ON `test_result` (`sessionId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_test_result_draftId` ON `test_result` (`draftId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_test_result_measurementBatchId` ON `test_result` (`measurementBatchId`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_test_result_measurementBatchId_factor` " +
                    "ON `test_result` (`measurementBatchId`, `factor`)",
            )
            db.execSQL(
                "ALTER TABLE `calibration` ADD COLUMN `signalMethod` TEXT NOT NULL " +
                    "DEFAULT 'LEGACY_MEAN_BR'",
            )
            db.execSQL("UPDATE `calibration` SET `status` = 'NEEDS_REVIEW'")
        }
    }
}
