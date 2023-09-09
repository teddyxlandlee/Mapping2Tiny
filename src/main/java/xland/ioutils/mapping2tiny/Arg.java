/*
 *    Mapping2Tiny, a simple mapping converter
 *    Copyright (C) 2023 teddyxlandlee
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package xland.ioutils.mapping2tiny;

import java.util.*;

final class Arg {
    private final String text;
    private final int type;

    private Arg(String text, int type) {
        Objects.requireNonNull(text, "text");
        this.text = text;
        this.type = Integer.signum(type);
    }

    static final int GNU = 1, UNIX = -1, NORMAL = 0;

    static Arg gnu(String s) { return new Arg(s, GNU); }
    static Arg unix(char c) { return new Arg(String.valueOf(c), UNIX); }
    static Arg normal(String s) { return new Arg(s, NORMAL); }

    boolean isUnix() { return type < 0; }
    boolean isGnu() { return type > 0; }
    boolean isNormal() { return type == NORMAL; }

    String getText() { return text; }

    String getNormalText() throws IllegalArgumentException {
        if (!isNormal()) throw new IllegalArgumentException(String.format(
                "Expect %s to be normal, got %s", this, isUnix() ? "unix" : "gnu"
                ));
        return getText();
    }

    private Character getChar() {
        return text.charAt(0);
    }

    @Override
    public String toString() {
        if (isGnu()) return "--".concat(text);
        else if (isUnix()) return "-".concat(text);
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Arg)) return false;
        final Arg o1 = (Arg) o;
        return this.text.equals(o1.text) && this.type == o1.type;
    }

    @Override
    public int hashCode() {
        return (text.hashCode() << 1) ^ type;
    }

    static final class ArgList implements Iterable<Arg> {
        private final List<Arg> args;
        private final Map<Character, String> unixMap;

        ArgList(List<Arg> args, Map<Character, String> unixMap) {
            this.args = args;
            this.unixMap = unixMap;
        }

        public /*@NotNull*/ Itr iterator() {
            return new Itr(args.iterator(), unixMap);
        }
    }

    static final class Itr implements Iterator<Arg> {
        private final Iterator<Arg> prev;
        private final Map<Character, String> unixMap;

        Itr(Iterator<Arg> prev, Map<Character, String> unixMap) {
            this.prev = prev;
            this.unixMap = unixMap;
        }

        @Override
        public boolean hasNext() {
            return prev.hasNext();
        }

        @Override
        public Arg next() {
            Arg next = prev.next();
            if (next.isUnix()) {
                final String s = unixMap.get(next.getChar());
                if (s == null)
                    throw new IllegalArgumentException("Unknown option: " + next);
                return gnu(s);
            }
            return next;
        }

        String nextNormalText() throws IllegalArgumentException, NoSuchElementException {
            return prev.next().getNormalText();
        }
    }

    static ArgList parse(Map<Character, String> unixMap, String... args) {
        List<Arg> list = new ArrayList<>(args.length);
        boolean asIs = false;
        for (String s : args) {
            if (s.isBlank()) continue;  // ignore blank string
            if (!asIs && s.charAt(0) == '-') {
                final int len = s.length();
                if (len == 1) {
                    list.add(normal("-"));
                } else if (s.charAt(1) != '-') {    // unix
                    for (int i = 1; i < len; i++) {
                        list.add(unix(s.charAt(i)));
                    }
                } else if (len == 2) {  // "--"
                    asIs = true;
                } else {    // gnu
                    list.add(gnu(s.substring(2)));
                }
            } else {    // literal
                list.add(normal(s));
            }
        }
        return new ArgList(list, unixMap);
    }
}
