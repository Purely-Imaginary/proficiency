package dev.amman.proficiency.event;

/**
 * Why a furnace shift-click paid "Smithing - Air".
 *
 * <p>{@code AbstractFurnaceMenu.quickMoveStack} calls the result slot twice. First
 * {@code slot.onQuickCraft(moved, preMoveCopy)}, which fires the smelted event with the pre-move
 * copy: the right item and count. Then, after the move has emptied the slot's own stack,
 * {@code slot.onTake(player, thatSameStack)}, which fires it again with an empty stack (or with
 * whatever did not fit in the inventory, still sitting in the furnace). Plain clicks fire once,
 * with the stack that was taken.
 *
 * <p>So the first event is the truth and the second one must pay nothing.
 */
public final class SmeltTake {

    private SmeltTake() {
    }

    /**
     * @param count       the event stack's count
     * @param stillInSlot the event stack is the very object still held by the furnace result slot,
     *                    so it was not taken
     * @return how many items this event really took, zero when it must pay nothing
     */
    public static int takenCount(int count, boolean stillInSlot) {
        return stillInSlot || count <= 0 ? 0 : count;
    }
}
