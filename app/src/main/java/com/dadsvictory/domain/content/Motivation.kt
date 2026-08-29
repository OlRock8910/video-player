package com.dadsvictory.domain.content

/**
 * The motivational engine.
 *
 * Two rules govern every line in this file:
 *
 *  1. Nothing shames him. No "you failed", no "don't ruin it", no guilt as fuel.
 *     Guilt is what he has been drinking and vaping *at*. It is not a tool here.
 *  2. Nothing promises. No health claims live in this file — those belong in
 *     [Facts] and [HealthMilestones] where they carry a source.
 *
 * Rotation is deterministic and exhaustive: every message in a category is shown
 * once before any of them is shown a second time, so the same line never lands
 * two mornings running.
 */
object Motivation {

    enum class Category(val id: String) {
        MORNING("morning"),
        AFTERNOON("afternoon"),
        EVENING("evening"),
        CRAVING("craving"),
        FAMILY("family"),
        STRENGTH("strength"),
        MONEY("money"),
        HEALTH("health"),
        RELAPSE("relapse"),
        MILESTONE("milestone"),
    }

    val MORNING: List<String> = listOf(
        "Good morning, Dad. Today is another chance to choose freedom.",
        "Your future self is counting on today's decision.",
        "One day at a time. You've got this.",
        "You don't have to win forever this morning. Just win today.",
        "Whatever yesterday was, today gets its own answer.",
        "Nothing has to be decided all at once. Just the next thing.",
        "The man who quits is the same man who woke up this morning. That's you.",
        "You've already done the hardest part: you decided.",
        "Today doesn't need to be perfect. It needs to be free.",
        "Start slow. Start steady. Just start free.",
        "Some mornings you feel it. Some mornings you just do it. Both count.",
        "You are not giving something up. You are getting something back.",
        "A quiet morning is a good morning to be free.",
        "Nobody has to see today's victory for it to be real.",
        "The first hour is yours. Then the next one.",
        "You've been strong before. Today is just more of the same.",
        "Freedom is built one ordinary morning at a time.",
        "You're not behind. You're exactly where the work is.",
        "Today, be the man your family already thinks you are.",
        "Wake up. Choose again. That's the whole method.",
        "You don't need motivation this morning. You need a decision, and you've made it.",
        "Every day you stay free, the old habit gets a little quieter.",
        "Give today a fair chance before you judge it.",
        "The best time to keep going is right now, first thing.",
        "You're doing something difficult on purpose. That's what strength looks like.",
        "Small, boring, faithful days are how big things get built.",
        "Today is a good day to be someone your grandchildren can lean on.",
        "You woke up free. Let's keep it that way.",
        "The craving doesn't get a vote today.",
        "One decision at a time, and today only needs today's.",
        "You've carried heavier things than this.",
        "Peace over pleasure. Freedom over a few minutes.",
        "Take today gently, but take it.",
        "Your body is doing quiet work this morning. Let it.",
        "Being here, still trying, is already a victory.",
        "This is the kind of day that adds up.",
        "You're not doing this alone, and you're not doing it for nothing.",
        "Start the day with water, not with a decision you'll regret.",
        "Whatever comes today, you get to meet it clear-headed.",
        "Good morning. You are still on the path.",
    )

    val AFTERNOON: List<String> = listOf(
        "Craving right now? Wait 10 minutes. Drink some water. Take a walk. Let the urge pass.",
        "You don't have to win forever today. Just win this moment.",
        "Remember why you started.",
        "The craving is temporary. Your goal is permanent.",
        "Don't negotiate with a craving. It doesn't argue in good faith.",
        "Halfway through the day and still free. That counts.",
        "If today is hard, make the goal smaller: don't vape for the next 10 minutes.",
        "Urges rise and fall. This one is already on its way down.",
        "You don't need to feel strong to make a strong decision.",
        "Stand up. Move. Change the room. Change the moment.",
        "A craving is a request, not an order.",
        "The afternoon slump is a feeling, not an instruction.",
        "Water first. Decide after.",
        "You've said no to this before. You know how.",
        "Nothing about this moment requires you to act on it.",
        "Ten minutes of discomfort is cheaper than starting over.",
        "This feeling will pass whether you give in or not. Let it pass the good way.",
        "Your future is worth more than the craving.",
        "One decision. One moment. One victory.",
        "Step outside for two minutes. That's the whole plan.",
        "Tired isn't the same as needing it.",
        "Stressed isn't the same as needing it.",
        "Bored isn't the same as needing it.",
        "You are allowed to just wait this out.",
        "Whatever it's promising you, it won't deliver.",
        "The relief it offers is the problem it created.",
        "You've made it this far into the day. Take it a bit further.",
        "Call someone. You don't even have to talk about this.",
        "Do the next small thing. Anything but that.",
        "Breathe out longer than you breathe in. Then decide.",
        "Being uncomfortable is not an emergency.",
        "You're not missing out. You're getting out.",
        "Right now is the whole battle. And right now is short.",
        "Put it off for ten minutes. You can always change your mind later — but you probably won't need to.",
        "Progress isn't perfection. Keep going.",
    )

    val EVENING: List<String> = listOf(
        "Another day of choosing freedom.",
        "Before bed: what was one victory you had today?",
        "Be proud of every craving you defeated.",
        "You did that. Nobody did it for you.",
        "Today is on the board. It can't be taken off.",
        "Rest tonight knowing you stayed on the path.",
        "Whatever today cost you, you paid it and you're still here.",
        "Tomorrow gets a fresh start. Tonight gets some rest.",
        "One more day added to the pile. The pile is getting big.",
        "You were quietly brave today.",
        "Nobody clapped, and you did it anyway.",
        "Your family got a better version of you today.",
        "Add today to the reasons you can trust yourself.",
        "Free today. That's the whole win.",
        "You held the line. Get some sleep.",
        "If today was hard, you should be even prouder of it.",
        "The days you didn't feel like it are the ones that count double.",
        "Look how far back the start line is now.",
        "Say it out loud: I stayed free today.",
        "Peace tonight instead of regret. Good trade.",
        "You'll wake up tomorrow glad about tonight's decision.",
        "One evening at a time is how a life changes.",
        "Whatever tomorrow brings, tonight you're free.",
        "That's another day your body didn't have to recover from.",
        "Write down one thing that went right today.",
        "Being consistent is not glamorous. It's just how it's done.",
        "You're becoming the person who doesn't do that any more.",
        "Sleep well. You've earned an easy conscience.",
        "Tomorrow, same plan: just today, again.",
        "You showed up for yourself today.",
        "This is what taking your life back actually looks like — quiet and daily.",
        "Set the alarm. Come back tomorrow. That's it.",
        "Today mattered even if nobody noticed.",
        "The streak is made of evenings like this one.",
        "Well done. Genuinely.",
    )

    val CRAVING: List<String> = listOf(
        "You don't have to act on this feeling.",
        "Cravings rise and fall.",
        "Give yourself 10 minutes.",
        "You're stronger than this moment.",
        "Keep breathing.",
        "This will pass. It always passes.",
        "You are not in danger. You're just uncomfortable.",
        "Stay here. Don't decide anything yet.",
        "The peak is short. You're near it.",
        "Slow the breath down. That's all you have to do.",
        "Every second you wait, it weakens.",
        "You've survived every single one of these before.",
        "Don't fight it. Just outlast it.",
        "Let it be there. It can't make you do anything.",
        "Water. Air. Movement. In that order.",
        "You are winning right now, by sitting still.",
        "This is the moment the whole thing turns on. And you're here for it.",
        "Nothing needs to happen in the next ten minutes.",
        "It's loud, and it's lying.",
        "Feel it go up. Now feel it come down.",
        "You're already past the worst part.",
        "One breath. Then one more.",
        "Keep your hands busy and your mind slow.",
        "Almost through.",
        "Nearly there. Hold on.",
        "This craving will be over and you'll still be free.",
        "You don't owe it anything.",
        "Ride it out. That's the skill.",
        "Not today. Not this one.",
        "You're doing it right now.",
    )

    val FAMILY: List<String> = listOf(
        "My family needs me healthy.",
        "Be there for my grandchildren.",
        "Be strong for the people I love.",
        "They don't need a perfect dad. They need you here.",
        "Somebody in your house is quietly proud of you.",
        "You're not just quitting. You're setting a standard.",
        "Every free day is a day you're more available to them.",
        "The people who love you would rather have you than anything you'd buy.",
        "You're showing them how a hard thing is done.",
        "This is what providing looks like too.",
        "Your future self and your family want the same thing.",
        "You are worth this to them. Believe that.",
        "One day they'll tell someone that their dad quit. Give them that story.",
        "The best inheritance is more years of you.",
        "You're doing this so you can be there for the people you love.",
    )

    val STRENGTH: List<String> = listOf(
        "You don't need to feel strong to make a strong decision.",
        "Discipline is just remembering what you want.",
        "You're allowed to find this hard and do it anyway.",
        "Strength isn't never wanting it. It's not taking it.",
        "You're building something that can't be taken from you.",
        "The urge is loud. You are steady.",
        "Doing it while it's difficult is the entire point.",
        "You've done harder things with less support.",
        "Nobody feels ready. They just start.",
        "Keep going. That's the whole strategy.",
        "Being tired doesn't make you weak.",
        "You're not failing when it's hard. You're working.",
        "The version of you that gets through this is already in there.",
        "You are more stubborn than a craving.",
        "Freedom is built one day at a time.",
        "Courage is mostly just showing up again.",
        "You get to decide what kind of man today makes you.",
        "Slow is fine. Stopping is not the same as quitting on yourself.",
        "You have beaten this before, hour by hour. Do it again.",
        "This is hard because it matters.",
    )

    val MONEY: List<String> = listOf(
        "That's money that stayed in your pocket.",
        "Every day free is money you didn't set fire to.",
        "It adds up faster than it ever felt like it was going out.",
        "That's a holiday, slowly assembling itself.",
        "Money you spent on that is money you're spending on them now.",
        "Nobody ever regretted the money they didn't spend on this.",
        "This is a pay rise you gave yourself.",
        "Small daily amounts built the habit. Small daily amounts are unbuilding it.",
        "You're not saving money. You're getting it back.",
        "Put it towards something you'll actually remember.",
        "The cost was never just the money — but the money is real.",
        "Look at that number and remember it used to go up in smoke.",
    )

    val HEALTH: List<String> = listOf(
        "Your body is doing quiet repair work you can't see.",
        "Breathing gets easier to take for granted again.",
        "Sleep, mood and appetite settle in their own time.",
        "You're giving your body a fair chance for the first time in a while.",
        "Clear head. Steady hands. Real rest.",
        "The mornings get better. Give them time.",
        "You'll notice it in small things first.",
        "This is what looking after yourself actually looks like.",
        "Fewer things to recover from tomorrow.",
        "Your health isn't a project. It's a direction, and you've changed it.",
        "Every free day is a day your body didn't have to compensate.",
        "You're not fixing everything. You're removing one big thing.",
    )

    val RELAPSE: List<String> = listOf(
        "One bad moment does not erase your progress.",
        "Thank you for being honest. That took more courage than hiding it.",
        "You're still on the journey.",
        "Get back up. Keep going.",
        "The days you stayed free still happened. Nothing takes them back.",
        "One slip does not erase the progress you've made.",
        "This is information, not a verdict.",
        "What matters now is the next decision, not the last one.",
        "Nobody does this in a straight line.",
        "You learned something today that you didn't know yesterday.",
        "Start again now. Not Monday. Now.",
        "The fall isn't the story. The getting up is.",
        "You are not back at the beginning. You know things now.",
        "Be as kind to yourself as you'd be to a mate in the same spot.",
        "Win the next decision. That's all that's being asked.",
    )

    val MILESTONE: List<String> = listOf(
        "Look what you've done.",
        "That's not luck. That's a hundred decisions.",
        "Somebody should tell you they're proud of you, so: I am.",
        "This is a real achievement. Sit with it for a second.",
        "You are further than you ever thought you'd get.",
        "Remember when this felt impossible?",
        "That number is made of hard days you got through.",
        "You did that on purpose.",
        "Keep the receipt on this one. You'll need it on a hard day.",
        "This is what keeping a promise to yourself looks like.",
        "Milestones are just ordinary days that added up.",
        "Well done. Now, the next one.",
    )

    val CORE_MESSAGE: String =
        "You don't have to be perfect. You just have to keep choosing freedom."

    fun messages(category: Category): List<String> = when (category) {
        Category.MORNING -> MORNING
        Category.AFTERNOON -> AFTERNOON
        Category.EVENING -> EVENING
        Category.CRAVING -> CRAVING
        Category.FAMILY -> FAMILY
        Category.STRENGTH -> STRENGTH
        Category.MONEY -> MONEY
        Category.HEALTH -> HEALTH
        Category.RELAPSE -> RELAPSE
        Category.MILESTONE -> MILESTONE
    }

    /** Total across every category — the app's own count, so the README can't drift from it. */
    fun totalMessageCount(): Int = Category.entries.sumOf { messages(it).size }

    /**
     * Picks message number [index] from a category, shuffling the whole list into a
     * fresh order each time it has been exhausted. Same index always gives the same
     * message, so a notification can be rebuilt after a reboot and still say what it
     * was going to say.
     */
    fun pick(category: Category, index: Long): String {
        val list = messages(category)
        return Rotation.pick(list, index)
    }
}

/**
 * Deterministic, exhaustive rotation.
 *
 * Every item appears exactly once per cycle; each cycle is in a different order;
 * and a cycle never opens with the item the previous cycle closed on, so nothing
 * repeats across the seam.
 */
object Rotation {

    fun <T> pick(items: List<T>, index: Long): T {
        require(items.isNotEmpty()) { "Cannot rotate an empty list" }
        if (items.size == 1) return items[0]
        val size = items.size.toLong()
        val cycle = Math.floorDiv(index, size)
        val position = Math.floorMod(index, size).toInt()
        return order(items.size, cycle)[position].let { items[it] }
    }

    /** Index permutation for one cycle. */
    fun order(size: Int, cycle: Long): List<Int> {
        val indices = (0 until size).toMutableList()
        var state = seed(cycle)
        // Fisher-Yates driven by a small deterministic PRNG.
        for (i in size - 1 downTo 1) {
            state = next(state)
            val j = (state % (i + 1).toLong()).toInt()
            val tmp = indices[i]
            indices[i] = indices[j]
            indices[j] = tmp
        }
        if (cycle > 0 && size > 1) {
            val previousLast = order(size, cycle - 1).last()
            if (indices.first() == previousLast) {
                // Rotate by one rather than reshuffling, which keeps this terminating.
                indices.add(indices.removeAt(0))
            }
        }
        return indices
    }

    /** Golden-ratio odd constant, written signed so no unsigned literal is needed. */
    private const val GOLDEN: Long = -0x61c8864680b583ebL

    private fun seed(cycle: Long): Long = (cycle + 1L) * GOLDEN

    private fun next(state: Long): Long {
        var x = state
        x = x xor (x ushr 30)
        x *= -0x40a7b892e31b1a47L
        x = x xor (x ushr 27)
        x *= -0x6b2fb644ecceee15L
        x = x xor (x ushr 31)
        return x and Long.MAX_VALUE
    }
}
