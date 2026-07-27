/*
    This file is part of the OdinMS Maple Story Server

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package client;

import tools.Randomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the real, client-computed damage totals a player has recently dealt, keyed by
 * (skill, monster species). Full Map Attack uses this as a source of truth: when it fabricates a
 * hit on a far-away monster, it replays a recent real total the player actually dealt to that same
 * monster type with that same skill - so the number already reflects that mob's defense and
 * elemental weakness, the skill's multiplier, mastery variance and crits, with no formula to
 * reimplement server-side.
 *
 * <p>Only a bounded window of recent totals is kept per key, so the memory adapts as buffs come
 * and go (a swing or two after a big damage buff may still reflect the older numbers until the
 * window refreshes - the accepted tradeoff for "use real numbers").
 */
public class FullMapAttackDamageMemory {
    private static final int MAX_SAMPLES_PER_KEY = 30;

    // key -> recent real per-mob totals. Outer map is concurrent; each sample list is guarded by
    // its own monitor since the attack handler may touch this off the client's packet thread.
    private final ConcurrentHashMap<Long, List<Integer>> samples = new ConcurrentHashMap<>();

    private static long key(int skillId, int mobId) {
        return ((long) skillId << 32) | (mobId & 0xFFFFFFFFL);
    }

    /**
     * Record a real damage total the player just dealt to a monster of type {@code mobId} using
     * {@code skillId} (0 for a basic attack). Non-positive totals are ignored.
     */
    public void record(int skillId, int mobId, int totalDamage) {
        if (totalDamage <= 0) {
            return;
        }
        List<Integer> list = samples.computeIfAbsent(key(skillId, mobId), k -> new ArrayList<>(MAX_SAMPLES_PER_KEY));
        synchronized (list) {
            list.add(totalDamage);
            if (list.size() > MAX_SAMPLES_PER_KEY) {
                list.remove(0);
            }
        }
    }

    /**
     * Replay one recent real total for the given skill against the given monster type, if any is
     * known. Returns empty when the player has not yet hit that mob type with that skill.
     */
    public OptionalInt sample(int skillId, int mobId) {
        List<Integer> list = samples.get(key(skillId, mobId));
        if (list == null) {
            return OptionalInt.empty();
        }
        synchronized (list) {
            if (list.isEmpty()) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(list.get(Randomizer.nextInt(list.size())));
        }
    }
}
