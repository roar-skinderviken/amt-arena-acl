package no.nav.amt.arena.acl.repositories

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.arena.acl.domain.db.ArenaDataUpsertInput
import no.nav.amt.arena.acl.domain.db.IngestStatus
import no.nav.amt.arena.acl.domain.kafka.amt.AmtOperation
import no.nav.amt.arena.acl.utils.DbUtils.isEqualTo
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest(classes = [ArenaDataRepository::class])
class ArenaDataRepositoryTest(
	dataRepository: ArenaDataRepository,
) : KotestRepositoryTestBase({
		beforeEach {
			val rootLogger: Logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
			rootLogger.level = Level.WARN
		}

		test("Insert and get should return inserted object") {
			val after = "{\"test\": \"test\"}"

			val data =
				ArenaDataUpsertInput(
					arenaTableName = "Table",
					arenaId = "ARENA_ID",
					operation = AmtOperation.CREATED,
					operationPosition = "1",
					operationTimestamp = LocalDateTime.now(),
					after = after,
				)

			dataRepository.upsert(data)

			val stored = dataRepository.get(data.arenaTableName, data.operation, data.operationPosition)

			stored shouldNotBe null
			stored!!.id shouldNotBe -1
			stored.arenaId shouldBe data.arenaId
			stored.before shouldBe null
			stored.after shouldBe after
		}

		test("retryDeltakereMedGjennomforingIdOgStatus - skal ikke feile") {
			val gjennomforingId = 123
			val afterDeltaker = "{\"TILTAKGJENNOMFORING_ID\": \"$gjennomforingId\"}"

			val data =
				ArenaDataUpsertInput(
					arenaTableName = "Table",
					arenaId = "ARENA_ID",
					operation = AmtOperation.CREATED,
					operationPosition = "1",
					operationTimestamp = LocalDateTime.now(),
					ingestStatus = IngestStatus.WAITING,
					after = afterDeltaker,
				)

			dataRepository.upsert(data)

			dataRepository.retryDeltakereMedGjennomforingIdOgStatus(gjennomforingId.toString(), listOf(IngestStatus.WAITING))
		}

		test("Upserting a Inserted object should modify it") {
			val data =
				ArenaDataUpsertInput(
					arenaTableName = "Table",
					arenaId = "ARENA_ID",
					operation = AmtOperation.CREATED,
					operationPosition = "1",
					operationTimestamp = LocalDateTime.now(),
					after = "{\"test\": \"test\"}",
				)

			dataRepository.upsert(data)

			val stored = dataRepository.get(data.arenaTableName, data.operation, data.operationPosition)

			val newIngestedTimestamp = LocalDateTime.now()

			val data2 =
				data.copy(
					ingestStatus = IngestStatus.RETRY,
					ingestedTimestamp = newIngestedTimestamp,
					note = "some note",
				)

			dataRepository.upsert(data2)

			val updated = dataRepository.get(data.arenaTableName, data.operation, data.operationPosition)

			stored!!.id shouldBe updated!!.id
			updated.ingestStatus shouldBe IngestStatus.RETRY
			updated.ingestedTimestamp!!.isEqualTo(newIngestedTimestamp) shouldBe true
			updated.note shouldBe "some note"
		}

		test("Should delete all ignored arena data") {
			val afterData = "{\"test\": \"test\"}"

			val data1 =
				ArenaDataUpsertInput(
					arenaTableName = "Table",
					arenaId = "ARENA_ID",
					operation = AmtOperation.CREATED,
					operationPosition = "1",
					operationTimestamp = LocalDateTime.now(),
					after = afterData,
				)

			val data2 =
				ArenaDataUpsertInput(
					arenaTableName = "Table",
					arenaId = "ARENA_ID",
					operation = AmtOperation.CREATED,
					operationPosition = "2",
					operationTimestamp = LocalDateTime.now(),
					ingestStatus = IngestStatus.IGNORED,
					after = afterData,
				)

			val data3 =
				ArenaDataUpsertInput(
					arenaTableName = "Table",
					arenaId = "ARENA_ID",
					operation = AmtOperation.CREATED,
					operationPosition = "3",
					operationTimestamp = LocalDateTime.now(),
					ingestStatus = IngestStatus.IGNORED,
					after = afterData,
				)

			dataRepository.upsert(data1)
			dataRepository.upsert(data2)
			dataRepository.upsert(data3)

			val rowsDeleted = dataRepository.deleteAllIgnoredData()

			val allData = dataRepository.getAll()

			rowsDeleted shouldBe 2
			allData.size shouldBe 1
		}
	})
