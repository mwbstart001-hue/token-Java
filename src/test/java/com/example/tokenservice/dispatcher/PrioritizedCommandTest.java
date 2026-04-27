package com.example.tokenservice.dispatcher;

import com.example.tokenservice.command.TokenCommand;
import com.example.tokenservice.command.TokenCommandType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PrioritizedCommand 优先级命令测试")
class PrioritizedCommandTest {

    @Test
    @DisplayName("创建 PrioritizedCommand - 应正确包含命令和优先级")
    void createPrioritizedCommand_ShouldContainCommandAndPriority() {
        TestCommand command = new TestCommand(TokenCommandType.GENERATE, "result");
        int priority = 10;

        PrioritizedCommand<String> prioritized = new PrioritizedCommand<>(command, priority);

        assertSame(command, prioritized.getCommand());
        assertEquals(priority, prioritized.getPriority());
    }

    @Test
    @DisplayName("创建 PrioritizedCommand - 命令为 null 时应抛出异常")
    void createPrioritizedCommand_WithNullCommand_ShouldThrowException() {
        assertThrows(NullPointerException.class, () -> {
            new PrioritizedCommand<>(null, 10);
        });
    }

    @Test
    @DisplayName("compareTo - 高优先级应排在前面")
    void compareTo_HighPriorityShouldComeFirst() {
        TestCommand lowCmd = new TestCommand(TokenCommandType.VALIDATE, "low");
        TestCommand highCmd = new TestCommand(TokenCommandType.GENERATE, "high");

        PrioritizedCommand<String> lowPriority = new PrioritizedCommand<>(lowCmd, 1);
        PrioritizedCommand<String> highPriority = new PrioritizedCommand<>(highCmd, 10);

        assertTrue(highPriority.compareTo(lowPriority) < 0);
        assertTrue(lowPriority.compareTo(highPriority) > 0);
    }

    @Test
    @DisplayName("compareTo - 相同优先级应保持一致性")
    void compareTo_SamePriority_ShouldBeConsistent() {
        TestCommand cmd1 = new TestCommand(TokenCommandType.GENERATE, "cmd1");
        TestCommand cmd2 = new TestCommand(TokenCommandType.VALIDATE, "cmd2");

        PrioritizedCommand<String> p1 = new PrioritizedCommand<>(cmd1, 5);
        PrioritizedCommand<String> p2 = new PrioritizedCommand<>(cmd2, 5);

        if (System.identityHashCode(p1) < System.identityHashCode(p2)) {
            assertTrue(p1.compareTo(p2) < 0);
            assertTrue(p2.compareTo(p1) > 0);
        } else {
            assertTrue(p2.compareTo(p1) < 0);
            assertTrue(p1.compareTo(p2) > 0);
        }
    }

    @Test
    @DisplayName("PriorityBlockingQueue - 应按优先级排序")
    void priorityBlockingQueue_ShouldOrderByPriority() {
        TestCommand lowCmd = new TestCommand(TokenCommandType.VALIDATE, "low");
        TestCommand mediumCmd = new TestCommand(TokenCommandType.GENERATE, "medium");
        TestCommand highCmd = new TestCommand(TokenCommandType.INVALIDATE, "high");

        PrioritizedCommand<String> lowPriority = new PrioritizedCommand<>(lowCmd, 1);
        PrioritizedCommand<String> mediumPriority = new PrioritizedCommand<>(mediumCmd, 5);
        PrioritizedCommand<String> highPriority = new PrioritizedCommand<>(highCmd, 10);

        PriorityBlockingQueue<PrioritizedCommand<String>> queue = new PriorityBlockingQueue<>();
        queue.offer(lowPriority);
        queue.offer(mediumPriority);
        queue.offer(highPriority);

        assertEquals(10, queue.poll().getPriority());
        assertEquals(5, queue.poll().getPriority());
        assertEquals(1, queue.poll().getPriority());
    }

    @Test
    @DisplayName("Collections.sort - 应按优先级降序排列")
    void collectionsSort_ShouldSortInDescendingOrder() {
        TestCommand cmd1 = new TestCommand(TokenCommandType.GENERATE, "cmd1");
        TestCommand cmd2 = new TestCommand(TokenCommandType.VALIDATE, "cmd2");
        TestCommand cmd3 = new TestCommand(TokenCommandType.INVALIDATE, "cmd3");

        PrioritizedCommand<String> p1 = new PrioritizedCommand<>(cmd1, 3);
        PrioritizedCommand<String> p2 = new PrioritizedCommand<>(cmd2, 1);
        PrioritizedCommand<String> p3 = new PrioritizedCommand<>(cmd3, 5);

        List<PrioritizedCommand<String>> list = new ArrayList<>();
        list.add(p1);
        list.add(p2);
        list.add(p3);

        Collections.sort(list);

        assertEquals(5, list.get(0).getPriority());
        assertEquals(3, list.get(1).getPriority());
        assertEquals(1, list.get(2).getPriority());
    }

    @Test
    @DisplayName("equals - 相同命令和优先级应相等")
    void equals_SameCommandAndPriority_ShouldBeEqual() {
        TestCommand cmd = new TestCommand(TokenCommandType.GENERATE, "test");

        PrioritizedCommand<String> p1 = new PrioritizedCommand<>(cmd, 10);
        PrioritizedCommand<String> p2 = new PrioritizedCommand<>(cmd, 10);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    @DisplayName("toString - 应包含类型和优先级信息")
    void toString_ShouldContainTypeAndPriority() {
        TestCommand cmd = new TestCommand(TokenCommandType.GENERATE, "test");
        PrioritizedCommand<String> prioritized = new PrioritizedCommand<>(cmd, 10);

        String str = prioritized.toString();
        assertTrue(str.contains("GENERATE"));
        assertTrue(str.contains("10"));
    }

    private static class TestCommand implements TokenCommand<String> {
        private final TokenCommandType type;
        private final String result;

        public TestCommand(TokenCommandType type, String result) {
            this.type = type;
            this.result = result;
        }

        @Override
        public String execute() {
            return result;
        }

        @Override
        public TokenCommandType getType() {
            return type;
        }
    }
}
