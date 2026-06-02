package com.codeint.objectboxkmp.runtime

class ObxField<T : Any, V>(
    val name: String,
    internal val valueOf: (T) -> V,
)

class ObxQueryBuilder<T : Any> internal constructor(
    private val source: () -> List<T>,
    private val removeEntity: (T) -> Boolean,
) {
    private val conditions = mutableListOf<ObxCondition<T>>()
    private val orderings = mutableListOf<ObxOrdering<T, *>>()
    private var limitValue: Int? = null
    private var offsetValue: Int = 0

    fun <V> equal(
        field: ObxField<T, V>,
        value: V,
    ): ObxQueryBuilder<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity) == value }
    }

    fun <V> notEqual(
        field: ObxField<T, V>,
        value: V,
    ): ObxQueryBuilder<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity) != value }
    }

    fun contains(
        field: ObxField<T, String>,
        value: String,
        ignoreCase: Boolean = true,
    ): ObxQueryBuilder<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity).contains(value, ignoreCase) }
    }

    fun startsWith(
        field: ObxField<T, String>,
        value: String,
        ignoreCase: Boolean = true,
    ): ObxQueryBuilder<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity).startsWith(value, ignoreCase) }
    }

    fun endsWith(
        field: ObxField<T, String>,
        value: String,
        ignoreCase: Boolean = true,
    ): ObxQueryBuilder<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity).endsWith(value, ignoreCase) }
    }

    fun <V : Comparable<V>> greaterThan(
        field: ObxField<T, V>,
        value: V,
    ): ObxQueryBuilder<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity) > value }
    }

    fun <V : Comparable<V>> lessThan(
        field: ObxField<T, V>,
        value: V,
    ): ObxQueryBuilder<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity) < value }
    }

    fun <V : Comparable<V>> between(
        field: ObxField<T, V>,
        from: V,
        to: V,
    ): ObxQueryBuilder<T> = apply {
        conditions += ObxCondition { entity ->
            val value = field.valueOf(entity)
            value >= from && value <= to
        }
    }

    fun anyOf(block: ObxQueryGroup<T>.() -> Unit): ObxQueryBuilder<T> = apply {
        val group = ObxQueryGroup<T>().apply(block)
        conditions += group.toAnyCondition()
    }

    fun allOf(block: ObxQueryGroup<T>.() -> Unit): ObxQueryBuilder<T> = apply {
        val group = ObxQueryGroup<T>().apply(block)
        conditions += group.toAllCondition()
    }

    fun <V : Comparable<V>> orderBy(
        field: ObxField<T, V>,
        descending: Boolean = false,
    ): ObxQueryBuilder<T> = apply {
        orderings += ObxOrdering(field, descending)
    }

    fun offset(value: Int): ObxQueryBuilder<T> = apply {
        if (value < 0) {
            throw ObxQueryException("ObjectBox KMP query offset must be >= 0, but was $value.")
        }
        offsetValue = value
    }

    fun limit(value: Int): ObxQueryBuilder<T> = apply {
        if (value < 0) {
            throw ObxQueryException("ObjectBox KMP query limit must be >= 0, but was $value.")
        }
        limitValue = value
    }

    fun build(): ObxQuery<T> {
        return ObxQuery(
            source = source,
            removeEntity = removeEntity,
            conditions = conditions.toList(),
            orderings = orderings.toList(),
            offset = offsetValue,
            limit = limitValue,
        )
    }

    fun find(): List<T> = build().find()

    fun findFirst(): T? = build().findFirst()

    fun count(): Long = build().count()

    fun remove(): Long = build().remove()
}

class ObxQuery<T : Any> internal constructor(
    private val source: () -> List<T>,
    private val removeEntity: (T) -> Boolean,
    private val conditions: List<ObxCondition<T>>,
    private val orderings: List<ObxOrdering<T, *>>,
    private val offset: Int,
    private val limit: Int?,
) {
    fun find(): List<T> {
        val filtered = source()
            .asSequence()
            .filter { entity -> conditions.all { condition -> condition.matches(entity) } }
            .toList()

        val ordered = orderings.fold(filtered) { items, ordering -> ordering.sort(items) }
        return ordered
            .drop(offset)
            .let { items -> limit?.let(items::take) ?: items }
    }

    fun findFirst(): T? = find().firstOrNull()

    fun count(): Long = find().size.toLong()

    fun remove(): Long {
        return find().count { entity -> removeEntity(entity) }.toLong()
    }
}

internal fun interface ObxCondition<T : Any> {
    fun matches(entity: T): Boolean
}

class ObxQueryGroup<T : Any> internal constructor() {
    private val conditions = mutableListOf<ObxCondition<T>>()

    fun <V> equal(
        field: ObxField<T, V>,
        value: V,
    ): ObxQueryGroup<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity) == value }
    }

    fun <V> notEqual(
        field: ObxField<T, V>,
        value: V,
    ): ObxQueryGroup<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity) != value }
    }

    fun contains(
        field: ObxField<T, String>,
        value: String,
        ignoreCase: Boolean = true,
    ): ObxQueryGroup<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity).contains(value, ignoreCase) }
    }

    fun startsWith(
        field: ObxField<T, String>,
        value: String,
        ignoreCase: Boolean = true,
    ): ObxQueryGroup<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity).startsWith(value, ignoreCase) }
    }

    fun endsWith(
        field: ObxField<T, String>,
        value: String,
        ignoreCase: Boolean = true,
    ): ObxQueryGroup<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity).endsWith(value, ignoreCase) }
    }

    fun <V : Comparable<V>> greaterThan(
        field: ObxField<T, V>,
        value: V,
    ): ObxQueryGroup<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity) > value }
    }

    fun <V : Comparable<V>> lessThan(
        field: ObxField<T, V>,
        value: V,
    ): ObxQueryGroup<T> = apply {
        conditions += ObxCondition { entity -> field.valueOf(entity) < value }
    }

    fun <V : Comparable<V>> between(
        field: ObxField<T, V>,
        from: V,
        to: V,
    ): ObxQueryGroup<T> = apply {
        conditions += ObxCondition { entity ->
            val value = field.valueOf(entity)
            value >= from && value <= to
        }
    }

    internal fun toAnyCondition(): ObxCondition<T> {
        if (conditions.isEmpty()) {
            throw ObxQueryException("ObjectBox KMP query anyOf group must contain at least one condition.")
        }
        return ObxCondition { entity -> conditions.any { condition -> condition.matches(entity) } }
    }

    internal fun toAllCondition(): ObxCondition<T> {
        if (conditions.isEmpty()) {
            throw ObxQueryException("ObjectBox KMP query allOf group must contain at least one condition.")
        }
        return ObxCondition { entity -> conditions.all { condition -> condition.matches(entity) } }
    }
}

internal class ObxOrdering<T : Any, V : Comparable<V>>(
    private val field: ObxField<T, V>,
    private val descending: Boolean,
) {
    fun sort(items: List<T>): List<T> {
        val comparator = compareBy<T> { entity -> field.valueOf(entity) }
        return if (descending) {
            items.sortedWith(comparator.reversed())
        } else {
            items.sortedWith(comparator)
        }
    }
}
