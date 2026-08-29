package com.dadsvictory.domain.content

/**
 * The Faith section.
 *
 * Only short passages, and only from translations that can be reproduced freely:
 * the World English Bible (public domain worldwide) and the King James Version
 * (public domain in the United States; Crown copyright in the UK, which permits
 * short quotations of this kind). No complete copyrighted translation is bundled.
 * See [Sources.SCRIPTURE_LICENSING], which is shown to the user in the app.
 */
enum class BibleVersion(val id: String, val abbreviation: String, val fullName: String, val note: String) {
    WEB(
        "web",
        "WEB",
        "World English Bible",
        "A modern-English translation in the public domain worldwide.",
    ),
    KJV(
        "kjv",
        "KJV",
        "King James Version",
        "The 1611 translation. Public domain in the US; short quotations permitted in the UK.",
    ),
    ;

    companion object {
        fun fromId(id: String?): BibleVersion = entries.firstOrNull { it.id == id } ?: WEB
    }
}

enum class VerseTheme(val id: String, val label: String, val emoji: String, val blurb: String) {
    STRENGTH("strength", "Strength", "🙏", "For when you have run out of your own."),
    PERSEVERANCE("perseverance", "Perseverance", "🙏", "For the long middle of a hard thing."),
    SELF_CONTROL("self_control", "Self-control", "🙏", "For the moment the urge arrives."),
    HOPE("hope", "Hope", "🙏", "For when it feels like it won't change."),
    COURAGE("courage", "Courage", "🙏", "For the days you have to walk in anyway."),
    PEACE("peace", "Peace", "🙏", "For a restless mind."),
    RENEWAL("renewal", "Renewal", "🙏", "For starting again."),
    NOT_GIVING_UP("not_giving_up", "Not giving up", "🙏", "For after a fall."),
    ;

    companion object {
        fun fromId(id: String?): VerseTheme = entries.firstOrNull { it.id == id } ?: STRENGTH
    }
}

data class Verse(
    val reference: String,
    val theme: VerseTheme,
    val web: String,
    val kjv: String,
) {
    fun text(version: BibleVersion): String = when (version) {
        BibleVersion.WEB -> web
        BibleVersion.KJV -> kjv
    }
}

object Scripture {

    val ALL: List<Verse> = listOf(
        // Strength
        Verse(
            "Philippians 4:13", VerseTheme.STRENGTH,
            "I can do all things through Christ, who strengthens me.",
            "I can do all things through Christ which strengtheneth me.",
        ),
        Verse(
            "Isaiah 41:10", VerseTheme.STRENGTH,
            "Don't you be afraid, for I am with you. Don't be dismayed, for I am your God. I will strengthen you. Yes, I will help you.",
            "Fear thou not; for I am with thee: be not dismayed; for I am thy God: I will strengthen thee; yea, I will help thee.",
        ),
        Verse(
            "Psalm 46:1", VerseTheme.STRENGTH,
            "God is our refuge and strength, a very present help in trouble.",
            "God is our refuge and strength, a very present help in trouble.",
        ),
        Verse(
            "2 Corinthians 12:9", VerseTheme.STRENGTH,
            "He has said to me, \"My grace is sufficient for you, for my power is made perfect in weakness.\"",
            "My grace is sufficient for thee: for my strength is made perfect in weakness.",
        ),
        Verse(
            "Nehemiah 8:10", VerseTheme.STRENGTH,
            "Don't be grieved, for the joy of Yahweh is your strength.",
            "Neither be ye sorry; for the joy of the LORD is your strength.",
        ),

        // Perseverance
        Verse(
            "James 1:4", VerseTheme.PERSEVERANCE,
            "Let endurance have its perfect work, that you may be perfect and complete, lacking in nothing.",
            "But let patience have her perfect work, that ye may be perfect and entire, wanting nothing.",
        ),
        Verse(
            "Hebrews 12:1", VerseTheme.PERSEVERANCE,
            "let's also lay aside every weight and the sin which so easily entangles us, and let's run with perseverance the race that is set before us.",
            "let us lay aside every weight, and the sin which doth so easily beset us, and let us run with patience the race that is set before us.",
        ),
        Verse(
            "Galatians 6:9", VerseTheme.PERSEVERANCE,
            "Let's not be weary in doing good, for we will reap in due season if we don't give up.",
            "And let us not be weary in well doing: for in due season we shall reap, if we faint not.",
        ),
        Verse(
            "Romans 5:3-4", VerseTheme.PERSEVERANCE,
            "knowing that suffering produces perseverance; and perseverance, proven character; and proven character, hope.",
            "knowing that tribulation worketh patience; And patience, experience; and experience, hope.",
        ),
        Verse(
            "James 1:12", VerseTheme.PERSEVERANCE,
            "Blessed is a person who endures temptation, for when he has been approved, he will receive the crown of life.",
            "Blessed is the man that endureth temptation: for when he is tried, he shall receive the crown of life.",
        ),

        // Self-control
        Verse(
            "Galatians 5:22-23", VerseTheme.SELF_CONTROL,
            "But the fruit of the Spirit is love, joy, peace, patience, kindness, goodness, faith, gentleness, and self-control.",
            "But the fruit of the Spirit is love, joy, peace, longsuffering, gentleness, goodness, faith, meekness, temperance.",
        ),
        Verse(
            "2 Timothy 1:7", VerseTheme.SELF_CONTROL,
            "For God didn't give us a spirit of fear, but of power, love, and self-control.",
            "For God hath not given us the spirit of fear; but of power, and of love, and of a sound mind.",
        ),
        Verse(
            "Proverbs 25:28", VerseTheme.SELF_CONTROL,
            "Like a city that is broken down and without walls is a man whose spirit is without restraint.",
            "He that hath no rule over his own spirit is like a city that is broken down, and without walls.",
        ),
        Verse(
            "1 Corinthians 10:13", VerseTheme.SELF_CONTROL,
            "No temptation has taken you except what is common to man. God is faithful, who will not allow you to be tempted above what you are able, but will with the temptation also make the way of escape, that you may be able to endure it.",
            "There hath no temptation taken you but such as is common to man: but God is faithful, who will not suffer you to be tempted above that ye are able; but will with the temptation also make a way to escape, that ye may be able to bear it.",
        ),
        Verse(
            "1 Corinthians 9:27", VerseTheme.SELF_CONTROL,
            "but I beat my body and bring it into submission.",
            "But I keep under my body, and bring it into subjection.",
        ),

        // Hope
        Verse(
            "Isaiah 40:31", VerseTheme.HOPE,
            "But those who wait for Yahweh will renew their strength. They will mount up with wings like eagles. They will run, and not be weary. They will walk, and not faint.",
            "But they that wait upon the LORD shall renew their strength; they shall mount up with wings as eagles; they shall run, and not be weary; and they shall walk, and not faint.",
        ),
        Verse(
            "Jeremiah 29:11", VerseTheme.HOPE,
            "For I know the thoughts that I think toward you, says Yahweh, thoughts of peace, and not of evil, to give you hope and a future.",
            "For I know the thoughts that I think toward you, saith the LORD, thoughts of peace, and not of evil, to give you an expected end.",
        ),
        Verse(
            "Romans 15:13", VerseTheme.HOPE,
            "Now may the God of hope fill you with all joy and peace in believing, that you may abound in hope in the power of the Holy Spirit.",
            "Now the God of hope fill you with all joy and peace in believing, that ye may abound in hope, through the power of the Holy Ghost.",
        ),
        Verse(
            "Lamentations 3:22-23", VerseTheme.HOPE,
            "It is because of Yahweh's loving kindnesses that we are not consumed, because his compassion doesn't fail. They are new every morning.",
            "It is of the LORD's mercies that we are not consumed, because his compassions fail not. They are new every morning: great is thy faithfulness.",
        ),
        Verse(
            "Psalm 42:11", VerseTheme.HOPE,
            "Why are you in despair, my soul? Why are you disturbed within me? Hope in God!",
            "Why art thou cast down, O my soul? and why art thou disquieted within me? hope thou in God.",
        ),

        // Courage
        Verse(
            "Joshua 1:9", VerseTheme.COURAGE,
            "Haven't I commanded you? Be strong and courageous. Don't be afraid. Don't be dismayed, for Yahweh your God is with you wherever you go.",
            "Have not I commanded thee? Be strong and of a good courage; be not afraid, neither be thou dismayed: for the LORD thy God is with thee whithersoever thou goest.",
        ),
        Verse(
            "Deuteronomy 31:6", VerseTheme.COURAGE,
            "Be strong and courageous. Don't be afraid or scared of them; for Yahweh your God himself is who goes with you. He will not fail you nor forsake you.",
            "Be strong and of a good courage, fear not, nor be afraid of them: for the LORD thy God, he it is that doth go with thee; he will not fail thee, nor forsake thee.",
        ),
        Verse(
            "Psalm 27:1", VerseTheme.COURAGE,
            "Yahweh is my light and my salvation. Whom shall I fear? Yahweh is the strength of my life. Of whom shall I be afraid?",
            "The LORD is my light and my salvation; whom shall I fear? the LORD is the strength of my life; of whom shall I be afraid?",
        ),
        Verse(
            "1 Corinthians 16:13", VerseTheme.COURAGE,
            "Watch! Stand firm in the faith! Be courageous! Be strong!",
            "Watch ye, stand fast in the faith, quit you like men, be strong.",
        ),
        Verse(
            "Psalm 31:24", VerseTheme.COURAGE,
            "Be strong, and let your heart take courage, all you who hope in Yahweh.",
            "Be of good courage, and he shall strengthen your heart, all ye that hope in the LORD.",
        ),

        // Peace
        Verse(
            "Philippians 4:6-7", VerseTheme.PEACE,
            "In nothing be anxious, but in everything, by prayer and petition with thanksgiving, let your requests be made known to God. And the peace of God, which surpasses all understanding, will guard your hearts and your thoughts in Christ Jesus.",
            "Be careful for nothing; but in every thing by prayer and supplication with thanksgiving let your requests be made known unto God. And the peace of God, which passeth all understanding, shall keep your hearts and minds through Christ Jesus.",
        ),
        Verse(
            "John 14:27", VerseTheme.PEACE,
            "Peace I leave with you. My peace I give to you. Don't let your heart be troubled, neither let it be fearful.",
            "Peace I leave with you, my peace I give unto you: let not your heart be troubled, neither let it be afraid.",
        ),
        Verse(
            "Matthew 11:28", VerseTheme.PEACE,
            "Come to me, all you who labor and are heavily burdened, and I will give you rest.",
            "Come unto me, all ye that labour and are heavy laden, and I will give you rest.",
        ),
        Verse(
            "Isaiah 26:3", VerseTheme.PEACE,
            "You will keep whoever's mind is steadfast in perfect peace, because he trusts in you.",
            "Thou wilt keep him in perfect peace, whose mind is stayed on thee: because he trusteth in thee.",
        ),
        Verse(
            "Psalm 23:1-3", VerseTheme.PEACE,
            "Yahweh is my shepherd; I shall lack nothing. He makes me lie down in green pastures. He leads me beside still waters. He restores my soul.",
            "The LORD is my shepherd; I shall not want. He maketh me to lie down in green pastures: he leadeth me beside the still waters. He restoreth my soul.",
        ),

        // Renewal
        Verse(
            "Romans 12:2", VerseTheme.RENEWAL,
            "Don't be conformed to this world, but be transformed by the renewing of your mind.",
            "And be not conformed to this world: but be ye transformed by the renewing of your mind.",
        ),
        Verse(
            "2 Corinthians 5:17", VerseTheme.RENEWAL,
            "Therefore if anyone is in Christ, he is a new creation. The old things have passed away. Behold, all things have become new.",
            "Therefore if any man be in Christ, he is a new creature: old things are passed away; behold, all things are become new.",
        ),
        Verse(
            "Ezekiel 36:26", VerseTheme.RENEWAL,
            "I will also give you a new heart, and I will put a new spirit within you.",
            "A new heart also will I give you, and a new spirit will I put within you.",
        ),
        Verse(
            "Isaiah 43:18-19", VerseTheme.RENEWAL,
            "Don't remember the former things, and don't consider the things of old. Behold, I will do a new thing.",
            "Remember ye not the former things, neither consider the things of old. Behold, I will do a new thing.",
        ),
        Verse(
            "Psalm 51:10", VerseTheme.RENEWAL,
            "Create in me a clean heart, O God. Renew a right spirit within me.",
            "Create in me a clean heart, O God; and renew a right spirit within me.",
        ),

        // Not giving up
        Verse(
            "Proverbs 24:16", VerseTheme.NOT_GIVING_UP,
            "for a righteous man falls seven times and rises up again.",
            "For a just man falleth seven times, and riseth up again.",
        ),
        Verse(
            "Micah 7:8", VerseTheme.NOT_GIVING_UP,
            "Don't rejoice against me, my enemy. When I fall, I will arise.",
            "Rejoice not against me, O mine enemy: when I fall, I shall arise.",
        ),
        Verse(
            "Philippians 3:13-14", VerseTheme.NOT_GIVING_UP,
            "forgetting the things which are behind and stretching forward to the things which are before, I press on toward the goal.",
            "forgetting those things which are behind, and reaching forth unto those things which are before, I press toward the mark.",
        ),
        Verse(
            "2 Corinthians 4:16", VerseTheme.NOT_GIVING_UP,
            "Therefore we don't faint, but though our outward person is decaying, yet our inward person is renewed day by day.",
            "For which cause we faint not; but though our outward man perish, yet the inward man is renewed day by day.",
        ),
        Verse(
            "Psalm 37:24", VerseTheme.NOT_GIVING_UP,
            "Though he stumble, he shall not fall, for Yahweh holds him up with his hand.",
            "Though he fall, he shall not be utterly cast down: for the LORD upholdeth him with his hand.",
        ),
    )

    fun byTheme(theme: VerseTheme): List<Verse> = ALL.filter { it.theme == theme }

    fun byReference(reference: String): Verse? = ALL.firstOrNull { it.reference == reference }

    /** Verse of the day — same verse all day, a different one tomorrow. */
    fun daily(epochDay: Long): Verse = Rotation.pick(ALL, epochDay)

    /** Themes that speak to a craving specifically. */
    val CRAVING_THEMES: List<VerseTheme> = listOf(
        VerseTheme.SELF_CONTROL,
        VerseTheme.STRENGTH,
        VerseTheme.PERSEVERANCE,
        VerseTheme.COURAGE,
        VerseTheme.NOT_GIVING_UP,
    )

    fun forCraving(index: Long): Verse {
        val pool = ALL.filter { it.theme in CRAVING_THEMES }
        return Rotation.pick(pool, index)
    }
}
