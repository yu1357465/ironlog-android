package io.github.yu1357465.ironlog

import io.github.yu1357465.ironlog.data.PlanShare
import io.github.yu1357465.ironlog.data.PlanShareException
import io.github.yu1357465.ironlog.data.SharedDay
import io.github.yu1357465.ironlog.data.SharedExercise
import io.github.yu1357465.ironlog.data.SharedPlan
import io.github.yu1357465.ironlog.data.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanShareTest {

    private val ppl = SharedPlan(
        "经典推拉腿 (PPL)",
        listOf(
            SharedDay("推力日", listOf(
                SharedExercise("杠铃平板卧推", MuscleGroup.CHEST, 4, 8, 60.0),
                SharedExercise("哑铃推举", MuscleGroup.SHOULDERS, 3, 10, 16.0),
            )),
            SharedDay("拉力日", listOf(SharedExercise("引体向上", MuscleGroup.BACK, 4, 8, 0.0))),
            SharedDay("腿部日", listOf(SharedExercise("杠铃深蹲", MuscleGroup.LEGS, 5, 5, 80.0))),
        ),
    )

    private fun withoutWeights(p: SharedPlan) =
        p.copy(days = p.days.map { d -> d.copy(exercises = d.exercises.map { it.copy(weightKg = null) }) })

    @Test
    fun codeRoundTrips() {
        val code = PlanShare.encode(ppl)
        assertTrue(code.startsWith(PlanShare.PREFIX))
        assertEquals(ppl, PlanShare.decode(code))
    }

    @Test
    fun decodesCodeFromWebVersion() {
        // 由 ironlog-web 后端 share.encode() 生成；两端格式改动时这里会失败（对应 web 的 test_decodes_code_from_android）
        val webCode = "FP1:eNqrViqpLEhVslJKyyzJSy0u1i3IScxT0lEqSy0qzszPU7Iy1FHKS8wFqXjWt-JZd-eL1v0KAQE-QCUpiZXFSlbR1UjyT7tmP5u-" +
            "FCiXWpFalJxZnIqqYO6Cl5Obn-7c_Gzu_qe9y4HqgSrTi_JLC4CSzh6uwSFAfnFqCVCTiY5SUWoBkGGho1SempmeUeKdrmRlZqBnUKsDN-_p5IlA" +
            "84DGPNmxD8mkYA__UB8X16BguGnGMNMMDWpjkQwA-uVl8wqCLn62feOLnZuQbPBxdUcYbgoz3BTZqRYgp8YCIQAvjYc_"
        val expected = SharedPlan("推拉腿 PPL", listOf(
            SharedDay("推力日", listOf(
                SharedExercise("杠铃平板卧推", MuscleGroup.CHEST, 4, 8, 60.0),
                SharedExercise("哑铃推举", MuscleGroup.SHOULDERS, 3, 10, null),
            )),
            SharedDay("腿部日", listOf(SharedExercise("杠铃深蹲", MuscleGroup.LEGS, 5, 5, 80.0))),
        ))
        assertEquals(expected, PlanShare.decode(webCode))
    }

    @Test
    fun jsonFileRoundTrips() {
        assertEquals(ppl, PlanShare.decode(PlanShare.toJson(ppl).toString(2)))
    }

    @Test
    fun weightsCanBeOmitted() {
        val plan = withoutWeights(ppl)
        val decoded = PlanShare.decode(PlanShare.encode(plan))
        assertFalse(decoded.includesWeights)
        assertNull(decoded.days[0].exercises[0].weightKg)
    }

    @Test
    fun codeFoundInsideChatMessage() {
        val message = "朋友：给你我的计划\n" + PlanShare.shareText(ppl) + "\n加油！"
        assertEquals(ppl, PlanShare.decode(message))
    }

    @Test
    fun typicalPlanFitsComfortablyInQrCode() {
        // 6 天 × 8 个动作，带重量；二维码（纠错等级 M）字节容量约 2300
        val big = SharedPlan("六分化", (1..6).map { d ->
            SharedDay("第 $d 天", (1..8).map { e -> SharedExercise("动作名称示例$d-$e", MuscleGroup.entries[e % 7], 4, 10, 42.5) })
        })
        val code = PlanShare.encode(big)
        assertTrue("length=${code.length}", code.length < 1500)
    }

    @Test
    fun truncatedCodeGivesFriendlyError() {
        val code = PlanShare.encode(ppl)
        val e = assertThrows(PlanShareException::class.java) { PlanShare.decode(code.dropLast(20)) }
        assertTrue(e.message!!.contains("不完整") || e.message!!.contains("损坏"))
    }

    @Test
    fun randomTextIsRejected() {
        val e = assertThrows(PlanShareException::class.java) { PlanShare.decode("今天练腿了吗") }
        assertTrue("分享码" in e.message!!)
    }

    @Test
    fun otherJsonIsRejected() {
        assertThrows(PlanShareException::class.java) { PlanShare.decode("""{"version":2,"exercises":[]}""") }
    }

    @Test
    fun valuesAreSanitised() {
        val json = """
            {"type":"fitness-plan","version":1,"name":"  我的   计划 ",
             "days":[{"name":"A","exercises":[
               {"name":"卧推","group":"CHEST","sets":0,"reps":5000,"weightKg":-5},
               {"name":"卧推","group":"CHEST","sets":3,"reps":8},
               {"name":"未知","group":"NOPE","sets":3,"reps":8}
             ]}]}
        """.trimIndent()
        val p = PlanShare.decode(json)
        assertEquals("我的 计划", p.name)
        val ex = p.days.single().exercises
        assertEquals(2, ex.size) // 同一天重复的动作只保留一个
        assertEquals(1, ex[0].sets)
        assertEquals(999, ex[0].reps)
        assertEquals(0.0, ex[0].weightKg!!, 0.0)
        assertEquals(MuscleGroup.OTHER, ex[1].group)
    }

    @Test
    fun fileNameIsSafe() {
        assertEquals("计划_A_B_C.json", PlanShare.fileName(ppl.copy(name = "A/B:C")))
    }
}
