package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.context.RecentEditHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentEditHistoryTest {
    @Test
    fun `stores small edit diffs oldest to newest and skips no-op edits`() {
        val history = RecentEditHistory()

        history.recordEdit("a.kt", "fun a() = 1\n", "fun a() = 2\n")
        history.recordEdit("b.kt", "fun b() = 1\n", "fun b() = 1\n")
        history.recordEdit("c.kt", "fun c() = 1\n", "fun c() = 3\n")

        val diffs = history.formattedDiffs()
        assertEquals(2, diffs.size)
        assertTrue(diffs[0].contains("--- a.kt"))
        assertTrue(diffs[0].contains("+fun a() = 2"))
        assertTrue(diffs[1].contains("--- c.kt"))
        assertTrue(diffs[1].contains("+fun c() = 3"))
    }

    @Test
    fun `coalesces repeated edits to an older location and moves it to most recent`() {
        val history = RecentEditHistory()
        val firstA =
            """
            enum AuthStatus {
                ErrorMessageBusHash,
                ErrorPredefinedReplicablesHash,
                ErrorUserKicked,
                ErrorPrefabHash,
            };
            """.trimIndent()
        val secondA = firstA.replace("ErrorUserKicked,", "ErrorUserKickedB,")
        val thirdA = firstA.replace("ErrorUserKicked,", "ErrorUserKickedAnticheat,")
        val fourthA = firstA.replace("ErrorUserKicked,", "ErrorUserKickedByAnticheat,")

        history.recordEdit("authentication_shared.hpp", firstA, secondA)
        history.recordEdit("other.hpp", "int value = 1\n", "int value = 2\n")
        history.recordEdit("authentication_shared.hpp", secondA, thirdA)
        history.recordEdit("authentication_shared.hpp", thirdA, fourthA)

        val diffs = history.formattedDiffs()
        assertEquals(2, diffs.size)
        assertTrue(diffs[0].contains("--- other.hpp"))
        assertTrue(diffs[1].contains("--- authentication_shared.hpp"))
        assertTrue(diffs[1].contains("-    ErrorUserKicked,"))
        assertTrue(diffs[1].contains("+    ErrorUserKickedByAnticheat,"))
        assertFalse(diffs[1].contains("+    ErrorUserKickedB,"))
        assertFalse(diffs[1].contains("+    ErrorUserKickedAnticheat,"))
    }

    @Test
    fun `coalesces nearby edit locations`() {
        val history = RecentEditHistory()
        val first =
            """
            switch (status) {
                case AuthStatus::Ok:
                    return "ok";
                case AuthStatus::WarningCommitHash:
                    return "commit hashes mismatch";
                case AuthStatus::ErrorReplicationHash:
                    return "replication schemes hashes mismatch";
                case AuthStatus::ErrorInputHash:
                    return "input registry hashes mismatch";
            }
            """.trimIndent()
        val second = first.replace("return \"commit hashes mismatch\";", "return \"Commit hashes mismatch\";")
        val third = second.replace("return \"replication schemes hashes mismatch\";", "return \"Replication schemes hashes mismatch\";")

        history.recordEdit("authentication.cpp", first, second)
        history.recordEdit("authentication.cpp", second, third)

        val diffs = history.formattedDiffs()
        assertEquals(1, diffs.size)
        assertTrue(diffs[0].contains("-        return \"commit hashes mismatch\";"))
        assertTrue(diffs[0].contains("+        return \"Commit hashes mismatch\";"))
        assertTrue(diffs[0].contains("-        return \"replication schemes hashes mismatch\";"))
        assertTrue(diffs[0].contains("+        return \"Replication schemes hashes mismatch\";"))
        assertTrue(diffs[0].contains("     case AuthStatus::ErrorReplicationHash:"))
        assertFalse(diffs[0].contains("-    case AuthStatus::ErrorReplicationHash:"))
        assertFalse(diffs[0].contains("+    case AuthStatus::ErrorReplicationHash:"))
    }

    @Test
    fun `keeps unchanged middle lines as context inside a coalesced diff`() {
        val history = RecentEditHistory()
        val first =
            """
            case AuthStatus::ErrorReplicationHash:
                return "replication schemes hashes mismatch";
            case AuthStatus::ErrorInputHash:
                return "input registry hashes mismatch";
            case AuthStatus::ErrorMessageBusHash:
                return "message bus registry hashes mismatch";
            case AuthStatus::ErrorPredefinedReplicablesHash:
                return "predefined replicables registry hashes mismatch";
            case AuthStatus::ErrorPrefabHash:
                return "prefab hashes mismatch";
            """.trimIndent()
        val second = first.replace("return \"replication schemes hashes mismatch\";", "return \"Replication schemes hashes mismatch\";")
        val third = second.replace(
            "return \"predefined replicables registry hashes mismatch\";",
            "return \"Predefined replicables registry hashes mismatch\";",
        )

        history.recordEdit("authentication.cpp", first, second)
        history.recordEdit("authentication.cpp", second, third)

        val diff = history.formattedDiffs().single()
        assertTrue(diff.contains("-    return \"replication schemes hashes mismatch\";"))
        assertTrue(diff.contains("+    return \"Replication schemes hashes mismatch\";"))
        assertTrue(diff.contains("-    return \"predefined replicables registry hashes mismatch\";"))
        assertTrue(diff.contains("+    return \"Predefined replicables registry hashes mismatch\";"))
        assertTrue(diff.contains(" case AuthStatus::ErrorInputHash:"))
        assertTrue(diff.contains("     return \"input registry hashes mismatch\";"))
        assertTrue(diff.contains(" case AuthStatus::ErrorMessageBusHash:"))
        assertTrue(diff.contains("     return \"message bus registry hashes mismatch\";"))
        assertTrue(diff.contains(" case AuthStatus::ErrorPredefinedReplicablesHash:"))
        assertFalse(diff.contains("-case AuthStatus::ErrorInputHash:"))
        assertFalse(diff.contains("+case AuthStatus::ErrorInputHash:"))
        assertFalse(diff.contains("-    return \"input registry hashes mismatch\";"))
        assertFalse(diff.contains("+    return \"input registry hashes mismatch\";"))
    }

    @Test
    fun `does not coalesce edit locations farther than five lines apart`() {
        val history = RecentEditHistory()
        val first =
            """
            int first = 1;
            int padding1 = 1;
            int padding2 = 1;
            int padding3 = 1;
            int padding4 = 1;
            int padding5 = 1;
            int padding6 = 1;
            int padding7 = 1;
            int second = 1;
            """.trimIndent()
        val second = first.replace("int first = 1;", "int first = 2;")
        val third = second.replace("int second = 1;", "int second = 2;")

        history.recordEdit("same.cpp", first, second)
        history.recordEdit("same.cpp", second, third)

        val diffs = history.formattedDiffs()
        assertEquals(2, diffs.size)
        assertTrue(diffs[0].contains("-int first = 1;"))
        assertTrue(diffs[0].contains("+int first = 2;"))
        assertTrue(diffs[1].contains("-int second = 1;"))
        assertTrue(diffs[1].contains("+int second = 2;"))
    }
}
