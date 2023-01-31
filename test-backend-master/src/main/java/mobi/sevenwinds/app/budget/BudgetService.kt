package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.stream.Collectors

object BudgetService {
    suspend fun addRecord(body: BudgetRecordRequest): BudgetRecordResponse = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.author_id = body.author_id
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val query = BudgetTable
                .select { BudgetTable.year eq param.year }
                .orderBy(BudgetTable.month to SortOrder.ASC, BudgetTable.amount to SortOrder.DESC)
                .limit(param.limit, param.offset)

            val total = query.count()
            val data = BudgetEntity.wrapRows(query).map { it.toResponse() }
            val sumByType = data.groupBy { it.type.name }.mapValues { it.value.sumOf { v -> v.amount } }
            val budgetRecordMapToAuthorsName = data.stream().filter { it.author_id !=  null }.collect(Collectors.toList())
                .map {it.id.toString() to  AuthorTable.select { AuthorTable.id eq it.author_id }.first()[AuthorTable.name] }.toMap()

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = data,
                budgetRecordIdAuthorsNameMap = budgetRecordMapToAuthorsName
            )
        }
    }
}