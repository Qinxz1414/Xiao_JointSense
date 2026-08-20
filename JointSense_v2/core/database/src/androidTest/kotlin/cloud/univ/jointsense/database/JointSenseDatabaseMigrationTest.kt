package cloud.univ.jointsense.database

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JointSenseDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JointSenseDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFrom1To2PreservesLegacyResultsAndValidatesExportedSchema() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                "INSERT INTO test_session (id, name, createdAt, source) " +
                    "VALUES ('session-1', 'Legacy test', 10, 'USER')",
            )
            execSQL(
                "INSERT INTO calibration (factor, createdAt, version, status, kitName, kitLot) " +
                    "VALUES ('IL6', 8, 1, 'ACTIVE', 'Legacy kit', 'lot-1')",
            )
            execSQL(
                """
                INSERT INTO test_result (
                    id, sessionId, draftId, factor, concentration, rangeStatus, timestamp,
                    rMean, gMean, bMean, rStd, gStd, bStd
                ) VALUES (
                    'result-1', 'session-1', 'legacy-draft', 'IL6', 12.5, 'IN_RANGE', 20,
                    90.0, 100.0, 110.0, 1.0, 2.0, 3.0
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            DatabaseMigrations.MIGRATION_1_2,
        ).use { migrated ->
            migrated.query(
                """
                SELECT id, sessionId, draftId, factor, concentration, rangeStatus, timestamp,
                    rMean, gMean, bMean, rStd, gStd, bStd, measurementBatchId, rawSignal, signalMethod
                FROM test_result
                WHERE id = 'result-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("result-1", cursor.string("id"))
                assertEquals("session-1", cursor.string("sessionId"))
                assertEquals("legacy-draft", cursor.string("draftId"))
                assertEquals("IL6", cursor.string("factor"))
                assertEquals(12.5f, cursor.float("concentration"))
                assertEquals("IN_RANGE", cursor.string("rangeStatus"))
                assertEquals(20L, cursor.long("timestamp"))
                assertEquals(90f, cursor.float("rMean"))
                assertEquals(100f, cursor.float("gMean"))
                assertEquals(110f, cursor.float("bMean"))
                assertEquals(1f, cursor.float("rStd"))
                assertEquals(2f, cursor.float("gStd"))
                assertEquals(3f, cursor.float("bStd"))
                assertNull(cursor.nullableString("measurementBatchId"))
                assertEquals(20f, cursor.float("rawSignal"))
                assertEquals("LEGACY_MEAN_BR", cursor.string("signalMethod"))
            }
            migrated.query(
                "SELECT status, signalMethod FROM calibration WHERE factor = 'IL6'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("NEEDS_REVIEW", cursor.string("status"))
                assertEquals("LEGACY_MEAN_BR", cursor.string("signalMethod"))
            }
            migrated.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'measurement_batch'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("measurement_batch", cursor.getString(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "jointsense-migration-test"
    }
}

private fun Cursor.column(name: String): Int = getColumnIndexOrThrow(name)

private fun Cursor.string(name: String): String = getString(column(name))

private fun Cursor.nullableString(name: String): String? =
    column(name).let { index -> if (isNull(index)) null else getString(index) }

private fun Cursor.float(name: String): Float = getFloat(column(name))

private fun Cursor.long(name: String): Long = getLong(column(name))
