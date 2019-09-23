package handlers.itemhandlers;

import org.l2j.gameserver.enums.ItemSkillType;
import org.l2j.gameserver.enums.ShotType;
import org.l2j.gameserver.handler.IItemHandler;
import org.l2j.gameserver.model.actor.Creature;
import org.l2j.gameserver.model.actor.Playable;
import org.l2j.gameserver.model.actor.Summon;
import org.l2j.gameserver.model.actor.instance.Player;
import org.l2j.gameserver.model.holders.ItemSkillHolder;
import org.l2j.gameserver.model.items.instance.Item;
import org.l2j.gameserver.network.SystemMessageId;
import org.l2j.gameserver.network.serverpackets.MagicSkillUse;
import org.l2j.gameserver.util.Broadcast;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.l2j.gameserver.util.GameUtils.isPlayer;

/**
 * Beast SoulShot Handler
 * @author Tempy
 */
public class BeastSoulShot implements IItemHandler {

    @Override
    public boolean useItem(Playable playable, Item item, boolean forceUse) {

        if (!isPlayer(playable)) {
            playable.sendPacket(SystemMessageId.YOUR_PET_CANNOT_CARRY_THIS_ITEM);
            return false;
        }

        final Player activeOwner = playable.getActingPlayer();
        if (!activeOwner.hasSummon()) {
            activeOwner.sendPacket(SystemMessageId.SERVITORS_ARE_NOT_AVAILABLE_AT_THIS_TIME);
            return false;
        }

        final Summon pet = playable.getPet();
        if ((pet != null) && pet.isDead()) {
            activeOwner.sendPacket(SystemMessageId.SOULSHOTS_AND_SPIRITSHOTS_ARE_NOT_AVAILABLE_FOR_A_DEAD_SERVITOR_SAD_ISN_T_IT);
            return false;
        }

        final List<Summon> aliveServitor = playable.getServitors().values().stream().filter(Predicate.not(Creature::isDead)).collect(Collectors.toList());
        if ((pet == null) && aliveServitor.isEmpty()) {
            activeOwner.sendPacket(SystemMessageId.SOULSHOTS_AND_SPIRITSHOTS_ARE_NOT_AVAILABLE_FOR_A_DEAD_SERVITOR_SAD_ISN_T_IT);
            return false;
        }

        final int itemId = item.getId();
        final long shotCount = item.getCount();
        final List<ItemSkillHolder> skills = item.getItem().getSkills(ItemSkillType.NORMAL);
        if (skills == null)
        {
            LOGGER.warn("is missing skills!");
            return false;
        }

        short shotConsumption = 0;

        if (pet != null)
        {
            if (!pet.isChargedShot(ShotType.SOULSHOTS))
            {
                shotConsumption += pet.getSoulShotsPerHit();
            }
        }

        for (Summon servitors : aliveServitor)
        {
            if (!servitors.isChargedShot(ShotType.SOULSHOTS))
            {
                shotConsumption += servitors.getSoulShotsPerHit();
            }
        }


        if (shotCount < shotConsumption)
        {
            // Not enough Soulshots to use.
            if (!activeOwner.disableAutoShot(itemId))
            {
                activeOwner.sendPacket(SystemMessageId.YOU_DON_T_HAVE_ENOUGH_SOULSHOTS_NEEDED_FOR_A_SERVITOR);
            }
            return false;
        }

        // If the player doesn't have enough beast soulshot remaining, remove any auto soulshot task.
        if (!activeOwner.destroyItemWithoutTrace("Consume", item.getObjectId(), shotConsumption, null, false))
        {
            if (!activeOwner.disableAutoShot(itemId))
            {
                activeOwner.sendPacket(SystemMessageId.YOU_DON_T_HAVE_ENOUGH_SOULSHOTS_NEEDED_FOR_A_SERVITOR);
            }
            return false;
        }

        // Pet uses the power of spirit.
        if (pet != null)
        {
            if (!pet.isChargedShot(ShotType.SOULSHOTS))
            {
                activeOwner.sendMessage("Your pet uses soulshot."); // activeOwner.sendPacket(SystemMessageId.YOUR_PET_USES_SPIRITSHOT);
                pet.chargeShot(ShotType.SOULSHOTS);
                // Visual effect change if player has equipped Ruby lvl 3 or higher
                if (activeOwner.getActiveRubyJewel() != null)
                {
                    Broadcast.toSelfAndKnownPlayersInRadius(activeOwner, new MagicSkillUse(pet, pet, activeOwner.getActiveRubyJewel().getEffectId(), 1, 0, 0), 600);
                }
                else
                {
                    skills.forEach(holder -> Broadcast.toSelfAndKnownPlayersInRadius(activeOwner, new MagicSkillUse(pet, pet, holder.getSkillId(), holder.getSkillLevel(), 0, 0), 600));
                }
            }
        }

        aliveServitor.forEach(s ->
        {
            if (!s.isChargedShot(ShotType.SOULSHOTS))
            {
                activeOwner.sendMessage("Your servitor uses soulshot."); // activeOwner.sendPacket(SystemMessageId.YOUR_PET_USES_SPIRITSHOT);
                s.chargeShot(ShotType.SOULSHOTS);
                // Visual effect change if player has equipped Ruby lvl 3 or higher
                if (activeOwner.getActiveRubyJewel() != null)
                {
                    Broadcast.toSelfAndKnownPlayersInRadius(activeOwner, new MagicSkillUse(s, s, activeOwner.getActiveRubyJewel().getEffectId(), 1, 0, 0), 600);
                }
                else
                {
                    skills.forEach(holder -> Broadcast.toSelfAndKnownPlayersInRadius(activeOwner, new MagicSkillUse(s, s, holder.getSkillId(), holder.getSkillLevel(), 0, 0), 600));
                }
            }
        });
        return true;
    }
}
