package com.nicgames.offthetop

import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    private val cards = listOf("Apple", "Bicycle", "Cloud", "Drum")

    private fun playing(duration: Int = 30, words: List<String> = cards): RoundEngine =
        RoundEngine(words, duration).apply {
            begin(0L)
            tick(3_000L)
            assertEquals(Phase.PLAYING, phase)
        }

    @Test fun initialStateMatchesPublicContract() {
        val engine = RoundEngine(words = cards, durationSeconds = 60)
        assertEquals(cards, engine.words)
        assertEquals(60, engine.durationSeconds)
        assertEquals(Phase.READY, engine.phase)
        assertEquals(3, engine.countdown)
        assertEquals(60_000L, engine.remainingMs)
        assertNull(engine.currentWord)
        assertNull(engine.feedback)
        assertEquals(emptyList<Answer>(), engine.answers)
        assertEquals(0, engine.score)
    }

    @Test fun rejectsEmptyAndBlankWords() {
        for (words in listOf(emptyList(), listOf(""), listOf(" \t\n"), listOf("Apple", " "))) {
            assertThrows(IllegalArgumentException::class.java) { RoundEngine(words, 30) }
        }
    }

    @Test fun rejectsDuplicateWordsIgnoringCaseAndSurroundingWhitespace() {
        for (words in listOf(listOf("Apple", "Apple"), listOf("Apple", "aPPLE"),
            listOf("Apple", " Apple "))) {
            assertThrows(IllegalArgumentException::class.java) { RoundEngine(words, 30) }
        }
    }

    @Test fun rejectsNonPositiveDurations() {
        for (duration in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { RoundEngine(cards, duration) }
        }
    }

    @Test fun acceptsAllUiDurationsAndPositiveCustomDurationsWithoutIntegerOverflow() {
        for (duration in listOf(1, 2, 30, 60, 90, 120, Int.MAX_VALUE)) {
            assertEquals(duration.toLong() * 1_000L, playing(duration).remainingMs)
        }
    }

    @Test fun readyStateIgnoresTimeInputPauseResumeAndReview() {
        val engine = RoundEngine(cards, 30)
        engine.tick(100_000L)
        engine.pause(100_000L)
        engine.resume(100_000L)
        engine.correctAnswer(0)
        for (outcome in Outcome.entries) assertFalse(engine.mark(outcome, 100_000L))
        assertEquals(Phase.READY, engine.phase)
        assertEquals(3, engine.countdown)
        assertEquals(30_000L, engine.remainingMs)
        assertTrue(engine.answers.isEmpty())
    }

    @Test fun countdownChangesAtExactSecondBoundaries() {
        val engine = RoundEngine(cards, 30)
        engine.begin(100L)
        for ((now, count) in listOf(100L to 3, 1_099L to 3, 1_100L to 2,
            2_099L to 2, 2_100L to 1, 3_099L to 1)) {
            engine.tick(now)
            assertEquals("countdown at $now", count, engine.countdown)
            assertEquals(Phase.COUNTDOWN, engine.phase)
            assertEquals(30_000L, engine.remainingMs)
            assertNull(engine.currentWord)
        }
        engine.tick(3_100L)
        assertEquals(Phase.PLAYING, engine.phase)
        assertEquals(0, engine.countdown)
        assertEquals("Apple", engine.currentWord)
        assertEquals(30_000L, engine.remainingMs)
        engine.tick(3_101L)
        assertEquals(29_999L, engine.remainingMs)
    }

    @Test fun lateFirstTickConsumesCountdownOverflowAsGameplayTime() {
        val engine = RoundEngine(cards, 30)
        engine.begin(1_000L)
        engine.tick(5_250L)
        assertEquals(Phase.PLAYING, engine.phase)
        assertEquals(28_750L, engine.remainingMs)
    }

    @Test fun oneLateTickCanFinishAnEntireRound() {
        val engine = RoundEngine(cards, 1)
        engine.begin(500L)
        engine.tick(1_000_000L)
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(listOf(Answer("Apple", Outcome.UNANSWERED)), engine.answers)
        assertEquals(0L, engine.remainingMs)
        assertEquals(0, engine.countdown)
        assertEquals(0, engine.score)
        assertNull(engine.currentWord)
    }

    @Test fun repeatedBeginCannotResetCountdownPlayingPausedOrFinished() {
        val engine = RoundEngine(cards, 30)
        engine.begin(0L)
        engine.begin(2_500L)
        engine.tick(3_000L)
        assertEquals(Phase.PLAYING, engine.phase)
        engine.begin(3_500L)
        engine.tick(4_000L)
        assertEquals(29_000L, engine.remainingMs)
        engine.pause(4_000L)
        engine.begin(9_000L)
        assertEquals(Phase.PAUSED, engine.phase)
        engine.finish(9_000L)
        engine.begin(20_000L)
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(1, engine.answers.size)
    }

    @Test fun countdownRejectsInputButMarkAtPlayingBoundaryWorksWithoutTick() {
        val engine = RoundEngine(cards, 30)
        engine.begin(0L)
        assertFalse(engine.mark(Outcome.CORRECT, 2_999L))
        assertTrue(engine.answers.isEmpty())
        assertTrue(engine.mark(Outcome.CORRECT, 3_000L))
        assertEquals(listOf(Answer("Apple", Outcome.CORRECT)), engine.answers)
        assertEquals(Outcome.CORRECT, engine.feedback)
        assertNull(engine.currentWord)
    }

    @Test fun correctAndPassAppendInOrderAndOnlyCorrectScores() {
        val engine = playing()
        assertTrue(engine.mark(Outcome.CORRECT, 3_000L))
        assertEquals(1, engine.score)
        assertEquals(Outcome.CORRECT, engine.feedback)
        assertNull(engine.currentWord)
        engine.tick(3_650L)
        assertEquals("Bicycle", engine.currentWord)
        assertTrue(engine.mark(Outcome.PASS, 3_650L))
        assertEquals(listOf(Answer("Apple", Outcome.CORRECT), Answer("Bicycle", Outcome.PASS)),
            engine.answers)
        assertEquals(1, engine.score)
        assertEquals(Outcome.PASS, engine.feedback)
        assertNull(engine.currentWord)
        engine.tick(4_300L)
        assertEquals("Cloud", engine.currentWord)
    }

    @Test fun unansweredCannotBeManuallyMarkedOrStartACooldown() {
        val engine = playing()
        assertFalse(engine.mark(Outcome.UNANSWERED, 3_000L))
        assertTrue(engine.answers.isEmpty())
        assertNull(engine.feedback)
        assertTrue(engine.mark(Outcome.PASS, 3_000L))
    }

    @Test fun everyInputWithinCooldownIsRejectedWithoutExtendingIt() {
        val engine = playing()
        assertTrue(engine.mark(Outcome.CORRECT, 3_000L))
        for (now in 3_000L..3_649L) {
            assertFalse("duplicate at $now", engine.mark(Outcome.CORRECT, now))
            assertFalse("pass at $now", engine.mark(Outcome.PASS, now))
        }
        assertEquals(1, engine.answers.size)
        assertEquals(1, engine.score)
        assertNull(engine.currentWord)
        engine.tick(3_650L)
        assertNull(engine.feedback)
        assertEquals("Bicycle", engine.currentWord)
        assertTrue(engine.mark(Outcome.CORRECT, 3_650L))
        assertEquals(2, engine.score)
    }

    @Test fun feedbackClearsOnTickExactlyAtCooldownEnd() {
        for (outcome in listOf(Outcome.CORRECT, Outcome.PASS)) {
            val engine = playing()
            assertTrue(engine.mark(outcome, 3_000L))
            assertNull(engine.currentWord)
            engine.tick(3_649L)
            assertEquals(outcome, engine.feedback)
            assertNull(engine.currentWord)
            engine.tick(3_650L)
            assertNull(engine.feedback)
            assertEquals("Bicycle", engine.currentWord)
            assertTrue(engine.mark(Outcome.PASS, 3_650L))
        }
    }

    @Test fun markThatClearsExpiredFeedbackCannotAlsoAnswerTheHiddenNextCard() {
        for (previous in listOf(Outcome.CORRECT, Outcome.PASS)) {
            for (outcome in Outcome.entries) {
                for (now in listOf(3_650L, 3_651L, 10_000L)) {
                    val engine = playing()
                    assertTrue(engine.mark(previous, 3_000L))
                    assertEquals(previous, engine.feedback)
                    assertNull(engine.currentWord)

                    assertFalse("$outcome at $now during $previous feedback",
                        engine.mark(outcome, now))

                    assertEquals(Phase.PLAYING, engine.phase)
                    assertEquals(33_000L - now, engine.remainingMs)
                    assertNull(engine.feedback)
                    assertEquals("Bicycle", engine.currentWord)
                    assertEquals(listOf(Answer("Apple", previous)), engine.answers)
                    assertEquals(if (previous == Outcome.CORRECT) 1 else 0, engine.score)
                }
            }
        }
    }

    @Test fun invalidInputDuringFeedbackDoesNotClearOrExtendCooldown() {
        val engine = playing()
        engine.mark(Outcome.CORRECT, 3_000L)
        assertFalse(engine.mark(Outcome.UNANSWERED, 3_400L))
        assertEquals(Outcome.CORRECT, engine.feedback)
        engine.tick(3_650L)
        assertNull(engine.feedback)
        assertEquals("Bicycle", engine.currentWord)
        assertTrue(engine.mark(Outcome.PASS, 3_650L))
    }

    @Test fun timerExpiresAtExactDeadlineAndCurrentCardIsUnansweredNotPassed() {
        val engine = playing(1)
        engine.tick(3_999L)
        assertEquals(Phase.PLAYING, engine.phase)
        assertEquals(1L, engine.remainingMs)
        engine.tick(4_000L)
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(listOf(Answer("Apple", Outcome.UNANSWERED)), engine.answers)
        assertEquals(0, engine.score)
        assertEquals(0L, engine.remainingMs)
    }

    @Test fun markAtOrAfterDeadlineAlwaysExpiresBeforeAcceptingAnyOutcome() {
        for (now in listOf(4_000L, 4_001L, 50_000L)) {
            for (outcome in Outcome.entries) {
                val engine = playing(1)
                assertFalse(engine.mark(outcome, now))
                assertEquals(Phase.FINISHED, engine.phase)
                assertEquals(listOf(Answer("Apple", Outcome.UNANSWERED)), engine.answers)
            }
        }
    }

    @Test fun markingJustBeforeDeadlineScoresButInputDuringFeedbackStillExpiresTheRound() {
        for (now in listOf(4_000L, 4_001L, 50_000L)) {
            for (outcome in Outcome.entries) {
                val engine = playing(1)
                assertTrue(engine.mark(Outcome.CORRECT, 3_999L))
                assertNull(engine.currentWord)
                assertFalse(engine.mark(outcome, now))
                assertEquals(Phase.FINISHED, engine.phase)
                assertEquals(listOf(Answer("Apple", Outcome.CORRECT)), engine.answers)
                assertEquals(1, engine.score)
                assertEquals(0L, engine.remainingMs)
                assertNull(engine.feedback)
                assertNull(engine.currentWord)
            }
        }
    }

    @Test fun timerExpiryDuringFeedbackNeverRecordsTheHiddenNextCardEvenOnALateTick() {
        for (outcome in listOf(Outcome.CORRECT, Outcome.PASS)) {
            for (now in listOf(4_000L, 4_001L, 50_000L)) {
                val engine = playing(1)
                assertTrue(engine.mark(outcome, 3_999L))
                assertEquals(outcome, engine.feedback)
                assertNull(engine.currentWord)
                engine.tick(now)
                assertEquals(Phase.FINISHED, engine.phase)
                assertEquals(listOf(Answer("Apple", outcome)), engine.answers)
                assertEquals(if (outcome == Outcome.CORRECT) 1 else 0, engine.score)
                assertEquals(0L, engine.remainingMs)
                assertNull(engine.feedback)
                assertNull(engine.currentWord)
            }
        }
    }

    @Test fun timerWinsWhenDeadlineAndCooldownEndCoincide() {
        val engine = playing(1)
        assertTrue(engine.mark(Outcome.CORRECT, 3_350L))
        assertFalse(engine.mark(Outcome.CORRECT, 4_000L))
        assertEquals(1, engine.score)
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(listOf(Answer("Apple", Outcome.CORRECT)), engine.answers)
        assertEquals(0L, engine.remainingMs)
        assertNull(engine.feedback)
        assertNull(engine.currentWord)
    }

    @Test fun singleWordRoundFinishesImmediatelyWithNoExtraUnansweredRow() {
        for (outcome in listOf(Outcome.CORRECT, Outcome.PASS)) {
            val engine = playing(words = listOf("Only card"))
            assertTrue(engine.mark(outcome, 3_000L))
            assertEquals(Phase.FINISHED, engine.phase)
            assertEquals(listOf(Answer("Only card", outcome)), engine.answers)
            assertNull(engine.currentWord)
            assertNull(engine.feedback)
            assertEquals(0L, engine.remainingMs)
            assertFalse(engine.mark(outcome, 3_650L))
        }
    }

    @Test fun wholeDeckIsUsedExactlyOnceAndInOrderWithoutWrapping() {
        val words = (1..100).map { "Card $it" }
        val engine = playing(120, words)
        words.forEachIndexed { index, word ->
            val now = 3_000L + index * 650L
            engine.tick(now)
            assertEquals(word, engine.currentWord)
            assertTrue(engine.mark(if (index % 2 == 0) Outcome.CORRECT else Outcome.PASS, now))
        }
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(words, engine.answers.map { it.word })
        assertEquals(100, engine.answers.map { it.word }.toSet().size)
        assertEquals(50, engine.score)
        engine.tick(1_000_000L)
        engine.finish(1_000_000L)
        assertEquals(100, engine.answers.size)
        assertNull(engine.currentWord)
    }

    @Test fun callerCannotChangeTheRoundByMutatingTheInputList() {
        val input = cards.toMutableList()
        val engine = playing(words = input)
        input.clear()
        input += "Replacement"
        assertEquals("Apple", engine.currentWord)
        assertTrue(engine.mark(Outcome.CORRECT, 3_000L))
        assertNull(engine.currentWord)
        engine.tick(3_650L)
        assertEquals("Bicycle", engine.currentWord)
    }

    @Test fun answerSnapshotsDoNotChangeWhenRoundAdvancesOrReviewEditsRows() {
        val engine = playing()
        engine.mark(Outcome.CORRECT, 3_000L)
        val snapshot = engine.answers
        engine.tick(3_650L)
        assertTrue(engine.mark(Outcome.PASS, 3_650L))
        engine.finish(3_650L)
        assertEquals(listOf(Answer("Apple", Outcome.CORRECT), Answer("Bicycle", Outcome.PASS)),
            engine.answers)
        engine.correctAnswer(0)
        assertEquals(listOf(Answer("Apple", Outcome.CORRECT)), snapshot)
        assertEquals(Outcome.PASS, engine.answers[0].outcome)
    }

    @Test fun countdownPausePreservesFractionalSecondAndDoesNotSpendRoundTime() {
        val engine = RoundEngine(cards, 30)
        engine.begin(0L)
        engine.pause(1_250L)
        assertEquals(Phase.PAUSED, engine.phase)
        assertEquals(2, engine.countdown)
        engine.tick(50_000L)
        engine.pause(50_000L)
        assertEquals(2, engine.countdown)
        assertEquals(30_000L, engine.remainingMs)
        assertFalse(engine.mark(Outcome.CORRECT, 50_000L))
        engine.resume(60_000L)
        assertEquals(Phase.COUNTDOWN, engine.phase)
        engine.tick(61_749L)
        assertEquals(Phase.COUNTDOWN, engine.phase)
        assertEquals(1, engine.countdown)
        engine.tick(61_750L)
        assertEquals(Phase.PLAYING, engine.phase)
        assertEquals(30_000L, engine.remainingMs)
        assertEquals("Apple", engine.currentWord)
    }

    @Test fun gameplayPauseUpdatesTimeThenFreezesAndResumeAddsFullCountdown() {
        val engine = playing(30)
        engine.pause(4_250L)
        assertEquals(28_750L, engine.remainingMs)
        assertEquals("Apple", engine.currentWord)
        engine.tick(100_000L)
        assertEquals(28_750L, engine.remainingMs)
        engine.resume(100_000L)
        assertEquals(Phase.COUNTDOWN, engine.phase)
        assertEquals(3, engine.countdown)
        assertEquals("Apple", engine.currentWord)
        assertFalse(engine.mark(Outcome.CORRECT, 102_999L))
        assertEquals(28_750L, engine.remainingMs)
        engine.tick(103_000L)
        assertEquals(Phase.PLAYING, engine.phase)
        assertEquals(28_750L, engine.remainingMs)
        engine.tick(103_001L)
        assertEquals(28_749L, engine.remainingMs)
    }

    @Test fun feedbackAndCooldownFreezeThroughoutPauseAndResumeCountdown() {
        for (outcome in listOf(Outcome.CORRECT, Outcome.PASS)) {
            val engine = playing()
            engine.mark(outcome, 3_000L)
            engine.pause(3_200L)
            engine.tick(100_000L)
            assertEquals(outcome, engine.feedback)
            assertNull(engine.currentWord)
            assertEquals(29_800L, engine.remainingMs)
            assertFalse(engine.mark(Outcome.CORRECT, 100_000L))
            engine.resume(100_000L)
            engine.tick(102_999L)
            assertEquals(outcome, engine.feedback)
            assertNull(engine.currentWord)
            engine.tick(103_000L)
            assertEquals(outcome, engine.feedback)
            assertNull(engine.currentWord)
            assertFalse(engine.mark(Outcome.CORRECT, 103_449L))
            assertEquals(outcome, engine.feedback)
            assertNull(engine.currentWord)
            engine.tick(103_450L)
            assertNull(engine.feedback)
            assertEquals("Bicycle", engine.currentWord)
            assertTrue(engine.mark(Outcome.PASS, 103_450L))
            assertEquals(2, engine.answers.size)
        }
    }

    @Test fun feedbackAlreadyExpiredWhenPausingStaysClearedAfterResume() {
        val engine = playing()
        engine.mark(Outcome.CORRECT, 3_000L)
        engine.pause(3_650L)
        assertNull(engine.feedback)
        engine.resume(10_000L)
        assertTrue(engine.mark(Outcome.PASS, 13_000L))
    }

    @Test fun pausingResumeCountdownPreservesCountdownTimeAndCooldown() {
        val engine = playing()
        engine.mark(Outcome.PASS, 3_000L)
        engine.pause(3_100L)
        engine.resume(10_000L)
        engine.pause(11_250L)
        assertEquals(2, engine.countdown)
        assertEquals(29_900L, engine.remainingMs)
        engine.resume(20_000L)
        engine.tick(21_749L)
        assertEquals(Phase.COUNTDOWN, engine.phase)
        engine.tick(21_750L)
        assertEquals(Phase.PLAYING, engine.phase)
        assertEquals(Outcome.PASS, engine.feedback)
        assertFalse(engine.mark(Outcome.CORRECT, 22_299L))
        engine.tick(22_300L)
        assertNull(engine.feedback)
        assertEquals("Bicycle", engine.currentWord)
        assertTrue(engine.mark(Outcome.CORRECT, 22_300L))
    }

    @Test fun lateTickAfterResumeConsumesOnlyTimeAfterResumeCountdown() {
        val engine = playing()
        engine.mark(Outcome.PASS, 3_000L)
        engine.pause(3_100L)
        engine.resume(10_000L)
        engine.tick(13_600L)
        assertEquals(29_300L, engine.remainingMs)
        assertNull(engine.feedback)
        assertTrue(engine.mark(Outcome.CORRECT, 13_600L))
    }

    @Test fun repeatedPauseAndResumeDoNotResetSavedTimeOrCountdown() {
        val engine = playing()
        engine.resume(3_500L)
        engine.pause(4_000L)
        engine.pause(8_000L)
        assertEquals(29_000L, engine.remainingMs)
        engine.resume(10_000L)
        engine.resume(12_000L)
        engine.tick(13_000L)
        assertEquals(Phase.PLAYING, engine.phase)
        assertEquals(29_000L, engine.remainingMs)
    }

    @Test fun pauseAtInitialCountdownBoundarySavesGameplayNotAnEmptyCountdown() {
        val engine = RoundEngine(cards, 30)
        engine.begin(0L)
        engine.pause(3_000L)
        assertEquals(Phase.PAUSED, engine.phase)
        assertEquals("Apple", engine.currentWord)
        engine.resume(10_000L)
        assertEquals(3, engine.countdown)
        engine.tick(12_999L)
        assertEquals(Phase.COUNTDOWN, engine.phase)
        engine.tick(13_000L)
        assertEquals(Phase.PLAYING, engine.phase)
    }

    @Test fun pauseAtOrAfterDeadlineCannotRescueExpiredRound() {
        for (now in listOf(4_000L, 4_001L, 100_000L)) {
            val engine = playing(1)
            engine.pause(now)
            assertEquals(Phase.FINISHED, engine.phase)
            assertEquals(listOf(Answer("Apple", Outcome.UNANSWERED)), engine.answers)
            engine.resume(now + 10_000L)
            assertEquals(Phase.FINISHED, engine.phase)
        }
    }

    @Test fun pauseOneMillisecondBeforeExpiryPreservesThatMillisecond() {
        val engine = playing(1)
        engine.pause(3_999L)
        engine.resume(100_000L)
        engine.tick(103_000L)
        assertEquals(Phase.PLAYING, engine.phase)
        assertEquals(1L, engine.remainingMs)
        engine.tick(103_001L)
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(Outcome.UNANSWERED, engine.answers.single().outcome)
    }

    @Test fun finishWhilePlayingRecordsOnlyCurrentCardAsUnanswered() {
        val engine = playing()
        engine.mark(Outcome.CORRECT, 3_000L)
        engine.tick(3_650L)
        assertEquals("Bicycle", engine.currentWord)
        engine.finish(3_750L)
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(listOf(Answer("Apple", Outcome.CORRECT), Answer("Bicycle", Outcome.UNANSWERED)),
            engine.answers)
        assertEquals(1, engine.score)
        assertNull(engine.currentWord)
        assertNull(engine.feedback)
    }

    @Test fun finishWhilePausedEndsWithoutResumingAndRecordsCurrentCardOnce() {
        val engine = playing()
        engine.mark(Outcome.PASS, 3_000L)
        engine.tick(3_650L)
        engine.pause(3_750L)
        assertEquals("Bicycle", engine.currentWord)
        engine.finish(1_000_000L)
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(listOf(Answer("Apple", Outcome.PASS), Answer("Bicycle", Outcome.UNANSWERED)),
            engine.answers)
        assertNull(engine.feedback)
        assertEquals(0L, engine.remainingMs)
        engine.finish(2_000_000L)
        assertEquals(2, engine.answers.size)
    }

    @Test fun finishDuringFeedbackNeverRecordsTheHiddenNextCard() {
        for (outcome in listOf(Outcome.CORRECT, Outcome.PASS)) {
            for (now in listOf(3_000L, 3_100L, 3_649L)) {
                val engine = playing()
                assertTrue(engine.mark(outcome, 3_000L))
                assertNull(engine.currentWord)
                engine.finish(now)
                assertEquals(Phase.FINISHED, engine.phase)
                assertEquals(listOf(Answer("Apple", outcome)), engine.answers)
                assertEquals(if (outcome == Outcome.CORRECT) 1 else 0, engine.score)
                assertEquals(0L, engine.remainingMs)
                assertNull(engine.currentWord)
                assertNull(engine.feedback)
            }
        }
    }

    @Test fun finishWhilePausedDuringFeedbackNeverRecordsTheHiddenNextCard() {
        for (outcome in listOf(Outcome.CORRECT, Outcome.PASS)) {
            val engine = playing()
            assertTrue(engine.mark(outcome, 3_000L))
            engine.pause(3_100L)
            assertEquals(Phase.PAUSED, engine.phase)
            assertEquals(outcome, engine.feedback)
            assertNull(engine.currentWord)
            engine.finish(1_000_000L)
            assertEquals(Phase.FINISHED, engine.phase)
            assertEquals(listOf(Answer("Apple", outcome)), engine.answers)
            assertEquals(if (outcome == Outcome.CORRECT) 1 else 0, engine.score)
            assertEquals(0L, engine.remainingMs)
            assertNull(engine.currentWord)
            assertNull(engine.feedback)
            engine.finish(2_000_000L)
            assertEquals(listOf(Answer("Apple", outcome)), engine.answers)
        }
    }

    @Test fun finishDuringResumeCountdownStillRecordsPreviouslyShownCard() {
        val engine = playing()
        engine.pause(3_100L)
        engine.resume(10_000L)
        engine.finish(11_000L)
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(listOf(Answer("Apple", Outcome.UNANSWERED)), engine.answers)
    }

    @Test fun finishBeforeFirstCardNeverInventsAnUnseenAnswer() {
        val ready = RoundEngine(cards, 30)
        val counting = RoundEngine(cards, 30).apply { begin(0L) }
        val paused = RoundEngine(cards, 30).apply { begin(0L); pause(1_000L) }
        for (engine in listOf(ready, counting, paused)) {
            engine.finish(2_000L)
            assertEquals(Phase.FINISHED, engine.phase)
            assertTrue(engine.answers.isEmpty())
            assertNull(engine.currentWord)
        }
    }

    @Test fun finishAtDeadlineDoesNotRecordTwoUnansweredCards() {
        val engine = playing(1)
        engine.finish(4_000L)
        assertEquals(listOf(Answer("Apple", Outcome.UNANSWERED)), engine.answers)
    }

    @Test fun finishedStateIgnoresEveryLifecycleAndInputOperation() {
        val engine = playing()
        engine.finish(3_000L)
        val answers = engine.answers
        repeat(10) { index ->
            val now = 10_000L + index * 10_000L
            engine.begin(now)
            engine.pause(now)
            engine.resume(now)
            engine.tick(now)
            engine.finish(now)
            for (outcome in Outcome.entries) assertFalse(engine.mark(outcome, now))
        }
        assertEquals(Phase.FINISHED, engine.phase)
        assertEquals(answers, engine.answers)
        assertEquals(0L, engine.remainingMs)
        assertNull(engine.feedback)
    }

    @Test fun reviewTogglesCorrectPassAndUnansweredWithoutAddingOrReorderingWords() {
        val engine = playing()
        assertTrue(engine.mark(Outcome.CORRECT, 3_000L))
        engine.tick(3_650L)
        assertTrue(engine.mark(Outcome.PASS, 3_650L))
        engine.tick(4_300L)
        assertEquals("Cloud", engine.currentWord)
        engine.finish(4_300L)
        assertEquals(listOf(Answer("Apple", Outcome.CORRECT), Answer("Bicycle", Outcome.PASS),
            Answer("Cloud", Outcome.UNANSWERED)), engine.answers)
        assertEquals(1, engine.score)
        engine.correctAnswer(0)
        assertEquals(Outcome.PASS, engine.answers[0].outcome)
        assertEquals(0, engine.score)
        engine.correctAnswer(1)
        assertEquals(Outcome.CORRECT, engine.answers[1].outcome)
        assertEquals(1, engine.score)
        engine.correctAnswer(2)
        assertEquals(Outcome.CORRECT, engine.answers[2].outcome)
        assertEquals(2, engine.score)
        engine.correctAnswer(2)
        assertEquals(Outcome.PASS, engine.answers[2].outcome)
        assertEquals(1, engine.score)
        engine.correctAnswer(0)
        assertEquals(2, engine.score)
        assertEquals(cards.take(3), engine.answers.map { it.word })
        assertEquals(Phase.FINISHED, engine.phase)
        assertNull(engine.currentWord)
        assertEquals(0L, engine.remainingMs)
    }

    @Test fun reviewIsIgnoredUntilFinishedIncludingPausedAndResumeCountdown() {
        val engine = playing()
        engine.mark(Outcome.CORRECT, 3_000L)
        engine.correctAnswer(0)
        engine.pause(3_100L)
        engine.correctAnswer(0)
        engine.resume(5_000L)
        engine.correctAnswer(0)
        assertEquals(Outcome.CORRECT, engine.answers.single().outcome)
        assertEquals(1, engine.score)
    }

    @Test fun invalidReviewIndexesSafelyLeaveResultsUnchanged() {
        val engine = playing()
        engine.finish(3_000L)
        val before = engine.answers
        for (index in listOf(Int.MIN_VALUE, -1, 1, Int.MAX_VALUE)) engine.correctAnswer(index)
        assertEquals(before, engine.answers)
        val empty = RoundEngine(cards, 30)
        empty.finish(0L)
        empty.correctAnswer(0)
        assertTrue(empty.answers.isEmpty())
    }

    @Test fun ticksAtSameOrEarlierTimeDoNotSpendTimeOrUndoFeedback() {
        val engine = playing()
        engine.mark(Outcome.CORRECT, 3_100L)
        engine.tick(3_300L)
        engine.tick(3_200L)
        engine.tick(3_300L)
        assertEquals(29_700L, engine.remainingMs)
        assertEquals(Outcome.CORRECT, engine.feedback)
        engine.tick(3_749L)
        assertEquals(Outcome.CORRECT, engine.feedback)
        engine.tick(3_750L)
        assertNull(engine.feedback)
    }

    @Test fun arbitraryMonotonicEpochsIncludingNearLongMaximumWork() {
        for (start in listOf(-100_000L, 0L, 8_000_000_000L, Long.MAX_VALUE - 4_000L)) {
            val engine = RoundEngine(cards, 1)
            engine.begin(start)
            engine.tick(start + 3_000L)
            assertEquals(Phase.PLAYING, engine.phase)
            engine.tick(start + 3_999L)
            assertEquals(1L, engine.remainingMs)
            engine.tick(start + 4_000L)
            assertEquals(Phase.FINISHED, engine.phase)
            assertEquals(1, engine.answers.size)
        }
    }

    @Test fun tickFrequencyDoesNotChangeResultsTimeOrFeedback() {
        val sparse = playing()
        val frequent = playing()
        for (engine in listOf(sparse, frequent)) engine.mark(Outcome.CORRECT, 3_000L)
        for (now in 3_001L..4_000L) frequent.tick(now)
        sparse.tick(4_000L)
        assertEquals(sparse.phase, frequent.phase)
        assertEquals(sparse.remainingMs, frequent.remainingMs)
        assertEquals(sparse.feedback, frequent.feedback)
        assertEquals(sparse.answers, frequent.answers)
        assertEquals(sparse.currentWord, frequent.currentWord)
    }
}