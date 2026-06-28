package com.kb.common.assertor;

import com.kb.common.exception.BusinessException;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KbAssertions 业务断言门面单元测试
 */
@DisplayName("KbAssertions 业务断言门面单元测试")
class KbAssertionsTest {

    // ===== 统一响应断言委托 =====

    @Test
    @DisplayName("assertSuccess_成功响应_委托AssertResult通过")
    void assertSuccess_validResult_delegatesPasses() {
        assertDoesNotThrow(() -> KbAssertions.assertSuccess(Result.ok("data")));
    }

    @Test
    @DisplayName("assertSuccess_错误响应_委托AssertResult抛出AssertionError")
    void assertSuccess_errorResult_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertSuccess(Result.fail(404, "不存在")));
    }

    @Test
    @DisplayName("assertSuccess_null结果_委托AssertResult抛出AssertionError")
    void assertSuccess_nullResult_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertSuccess(null));
    }

    @Test
    @DisplayName("assertBusinessError_匹配错误码_委托通过")
    void assertBusinessError_matchingCode_delegatesPasses() {
        assertDoesNotThrow(() ->
                KbAssertions.assertBusinessError(Result.fail(404, "不存在"), 404));
    }

    @Test
    @DisplayName("assertBusinessError_错误码不匹配_委托抛出AssertionError")
    void assertBusinessError_mismatch_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertBusinessError(Result.fail(403, "无权限"), 404));
    }

    @Test
    @DisplayName("assertDataNotNull_非空data_委托通过")
    void assertDataNotNull_validData_delegatesPasses() {
        assertDoesNotThrow(() -> KbAssertions.assertDataNotNull(Result.ok("data")));
    }

    @Test
    @DisplayName("assertDataNotNull_null的data_委托抛出AssertionError")
    void assertDataNotNull_nullData_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertDataNotNull(Result.ok()));
    }

    @Test
    @DisplayName("assertPageResult_有效分页_委托通过")
    void assertPageResult_validPage_delegatesPasses() {
        PageResult<String> page = PageResult.of(Arrays.asList("a"), 1L, 1, 10);
        Result<PageResult<String>> result = Result.ok(page);

        assertDoesNotThrow(() -> KbAssertions.assertPageResult(result, 1L));
    }

    @Test
    @DisplayName("assertPageResult_总数不匹配_委托抛出AssertionError")
    void assertPageResult_wrongTotal_delegatesThrows() {
        PageResult<String> page = PageResult.of(Arrays.asList("a"), 1L, 1, 10);
        Result<PageResult<String>> result = Result.ok(page);

        assertThrows(AssertionError.class,
                () -> KbAssertions.assertPageResult(result, 99L));
    }

    // ===== 异常断言委托 =====

    @Test
    @DisplayName("assertBusinessException_匹配code_委托通过")
    void assertBusinessException_matchingCode_delegatesPasses() {
        assertDoesNotThrow(() ->
                KbAssertions.assertBusinessException(
                        () -> { throw new BusinessException(404, "不存在"); }, 404));
    }

    @Test
    @DisplayName("assertBusinessException_code不匹配_委托抛出AssertionError")
    void assertBusinessException_mismatch_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertBusinessException(
                        () -> { throw new BusinessException(403, "无权限"); }, 404));
    }

    @Test
    @DisplayName("assertBusinessException_带message匹配_委托通过")
    void assertBusinessException_withMessage_delegatesPasses() {
        assertDoesNotThrow(() ->
                KbAssertions.assertBusinessException(
                        () -> { throw new NotFoundException("用户", 1L); }, 404, "用户"));
    }

    @Test
    @DisplayName("assertBusinessException_带message不匹配_委托抛出AssertionError")
    void assertBusinessException_withMessageMismatch_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertBusinessException(
                        () -> { throw new BusinessException(404, "资源"); }, 404, "用户"));
    }

    @Test
    @DisplayName("assertNoException_无异常_委托通过")
    void assertNoException_noException_delegatesPasses() {
        assertDoesNotThrow(() -> KbAssertions.assertNoException(() -> {}));
    }

    @Test
    @DisplayName("assertNoException_抛出异常_委托抛出AssertionError")
    void assertNoException_throws_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertNoException(
                        () -> { throw new RuntimeException("boom"); }));
    }

    @Test
    @DisplayName("assertExceptionType_类型匹配_委托通过")
    void assertExceptionType_matching_delegatesPasses() {
        assertDoesNotThrow(() ->
                KbAssertions.assertExceptionType(
                        () -> { throw new NotFoundException("x", 1L); },
                        NotFoundException.class));
    }

    @Test
    @DisplayName("assertExceptionType_类型不匹配_委托抛出AssertionError")
    void assertExceptionType_mismatch_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertExceptionType(
                        () -> { throw new RuntimeException("x"); },
                        BusinessException.class));
    }

    // ===== 字段断言委托 =====

    @Test
    @DisplayName("assertNotBlank_非空_委托通过")
    void assertNotBlank_valid_delegatesPasses() {
        assertDoesNotThrow(() -> KbAssertions.assertNotBlank("name", "value"));
    }

    @Test
    @DisplayName("assertNotBlank_null_委托抛出AssertionError")
    void assertNotBlank_null_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertNotBlank("name", null));
    }

    @Test
    @DisplayName("assertIdNotNull_正数_委托通过")
    void assertIdNotNull_positive_delegatesPasses() {
        assertDoesNotThrow(() -> KbAssertions.assertIdNotNull(1L));
    }

    @Test
    @DisplayName("assertIdNotNull_null_委托抛出AssertionError")
    void assertIdNotNull_null_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertIdNotNull(null));
    }

    @Test
    @DisplayName("assertCreated_实体有id_委托通过")
    void assertCreated_valid_delegatesPasses() {
        TestEntity entity = new TestEntity();
        entity.id = 1L;

        assertDoesNotThrow(() -> KbAssertions.assertCreated(entity));
    }

    @Test
    @DisplayName("assertCreated_null实体_委托抛出AssertionError")
    void assertCreated_null_delegatesThrows() {
        assertThrows(AssertionError.class,
                () -> KbAssertions.assertCreated(null));
    }

    @Test
    @DisplayName("assertUpdated_字段变更_委托通过")
    void assertUpdated_changed_delegatesPasses() {
        TestEntity before = new TestEntity();
        before.name = "old";
        TestEntity after = new TestEntity();
        after.name = "new";

        assertDoesNotThrow(() -> KbAssertions.assertUpdated(before, after, "name"));
    }

    @Test
    @DisplayName("assertUpdated_字段未变更_委托抛出AssertionError")
    void assertUpdated_unchanged_delegatesThrows() {
        TestEntity before = new TestEntity();
        before.name = "same";
        TestEntity after = new TestEntity();
        after.name = "same";

        assertThrows(AssertionError.class,
                () -> KbAssertions.assertUpdated(before, after, "name"));
    }

    // ===== 私有构造器 =====

    @Test
    @DisplayName("私有构造器_反射调用_实例化成功")
    void privateConstructor_canBeInvokedViaReflection() throws Exception {
        java.lang.reflect.Constructor<KbAssertions> constructor =
                KbAssertions.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        KbAssertions instance = constructor.newInstance();
        assertNotNull(instance);
    }

    static class TestEntity {
        Long id;
        String name;
    }
}
