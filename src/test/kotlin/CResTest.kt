import kotlin.test.Test
import kotlin.test.assertEquals

class CResTest {
	data class TestContext(
		val header: String = "TEST",
	) {
		fun log(msg: Any) {
			println("[${this.header}] $msg")
		}
	}

	sealed class TestError(msg: String) : Throwable(message = msg) {
		data object FatalFail : TestError("Fatal test case")
		data class ValueFail(val value: String) : TestError("Will recover?")
	}

	@Test
	fun checkContextMapping() {
		val context = TestContext()
		fun step1(context: TestContext): CRes<TestContext, Int, TestError> {
			return 20.wrapOk(context)
		}

		fun step2(context: TestContext, value: Int): CRes<TestContext, String, TestError> {
			return value.toString().wrapOk(context)
		}

		fun step3Error(context: TestContext, value: String): CRes<TestContext, String, TestError> {
			return TestError.ValueFail(value).wrapError(context)
		}

		fun recoverFromError(context: TestContext, error: TestError): CRes<TestContext, String, TestError> {
			if (error is TestError.ValueFail) return error.value.wrapOk(context)
			return TestError.FatalFail.wrapError(context)
		}

		val result = step1(context)
			.chainOk(::step2)
			.onOk { testContext, i -> testContext.log(i) }
			.chainOk(::step3Error)
			.onError { testContext, testError -> testContext.log(testError) }
			.chainError(::recoverFromError)
			.get()
		assertEquals("20", result)
	}
}