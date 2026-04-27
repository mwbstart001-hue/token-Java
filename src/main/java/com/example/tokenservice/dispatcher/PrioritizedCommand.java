package com.example.tokenservice.dispatcher;

import com.example.tokenservice.command.TokenCommand;

import java.util.Objects;

public class PrioritizedCommand<R> implements Comparable<PrioritizedCommand<R>> {

    private final TokenCommand<R> command;
    private final int priority;

    public PrioritizedCommand(TokenCommand<R> command, int priority) {
        this.command = Objects.requireNonNull(command, "TokenCommand cannot be null");
        this.priority = priority;
    }

    public TokenCommand<R> getCommand() {
        return command;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public int compareTo(PrioritizedCommand<R> other) {
        int result = Integer.compare(other.priority, this.priority);
        if (result == 0) {
            result = Integer.compare(System.identityHashCode(this), System.identityHashCode(other));
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrioritizedCommand<?> that = (PrioritizedCommand<?>) o;
        return priority == that.priority && Objects.equals(command, that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, priority);
    }

    @Override
    public String toString() {
        return "PrioritizedCommand{" +
                "commandType=" + command.getType() +
                ", priority=" + priority +
                '}';
    }
}
