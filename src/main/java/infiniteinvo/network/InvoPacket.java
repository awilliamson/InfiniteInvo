package infiniteinvo.network;

import infiniteinvo.achievements.InvoAchievements;
import infiniteinvo.core.II_Settings;
import infiniteinvo.core.InfiniteInvo;
import infiniteinvo.core.XPHelper;
import infiniteinvo.handlers.EventHandler;
import infiniteinvo.inventory.SlotLockable;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.Level;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class InvoPacket implements IMessage
{
	NBTTagCompound tags = new NBTTagCompound();
	
	public InvoPacket()
	{
	}
	
	public InvoPacket(NBTTagCompound tags)
	{
		this.tags = tags;
	}
	
	@Override
	public void fromBytes(ByteBuf buf)
	{
		tags = ByteBufUtils.readTag(buf);
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		ByteBufUtils.writeTag(buf, tags);
	}
	
	public static class HandleServer implements IMessageHandler<InvoPacket,IMessage>
	{
		@SuppressWarnings("unchecked")
		@Override
		public IMessage onMessage(InvoPacket message, MessageContext ctx)
		{
			if(message.tags.hasKey("ID"))
			{
				if(message.tags.getInteger("ID") == 0)
				{
					WorldServer world = MinecraftServer.getServer().worldServerForDimension(message.tags.getInteger("World"));
					
					if(world == null)
					{
						return null;
					}
					
					EntityPlayer player = world.getPlayerEntityByName(message.tags.getString("Player"));
					
					if(player == null)
					{
						return null;
					}

					int unlocked = player.getEntityData().getInteger("INFINITE_INVO_UNLOCKED");
					int cost = II_Settings.unlockCost + (unlocked * II_Settings.unlockIncrease);
					int totalXP = XPHelper.getPlayerXP(player);
					
					if(totalXP >= (II_Settings.useOrbs? cost : XPHelper.getLevelXP(cost)))
					{
						if(II_Settings.useOrbs)
						{
							XPHelper.AddXP(player, -cost);
						} else
						{
							XPHelper.AddXP(player, -XPHelper.getLevelXP(cost));
						}
						
						unlocked++;
						player.getEntityData().setInteger("INFINITE_INVO_UNLOCKED", unlocked);
						
						EventHandler.unlockCache.put(player.getCommandSenderName(), unlocked);
						
						if(unlocked > 0 || !II_Settings.xpUnlock)
						{
							player.addStat(InvoAchievements.unlockFirst, 1);
						}
						
						if(unlocked + II_Settings.unlockedSlots == II_Settings.invoSize || !II_Settings.xpUnlock)
						{
							player.addStat(InvoAchievements.unlockAll, 1);
						}
						
						NBTTagCompound replyTags = new NBTTagCompound();
						replyTags.setInteger("ID", 0);
						replyTags.setString("Player", player.getCommandSenderName());
						replyTags.setInteger("Unlocked", unlocked);
						return new InvoPacket(replyTags);
						
					}
				} else if(message.tags.getInteger("ID") == 1)
				{
					WorldServer world = MinecraftServer.getServer().worldServerForDimension(message.tags.getInteger("World"));
					
					if(world == null)
					{
						InfiniteInvo.logger.log(Level.WARN, "Unlock Sync Failed! Reason: Unabled to locate dimension " + message.tags.getInteger("World"));
						return null;
					}
					
					EntityPlayer player = world.getPlayerEntityByName(message.tags.getString("Player"));
					
					if(player == null || player.getEntityData() == null)
					{
						InfiniteInvo.logger.log(Level.WARN, "Unlock Sync Failed! Reason: Unabled to get data for player '" + message.tags.getString("Player") + "'");
						return null;
					}
					
					int unlocked = 0;
					
					if(!player.getEntityData().hasKey("INFINITE_INVO_UNLOCKED") && (EventHandler.unlockCache.containsKey(player.getCommandSenderName()) || EventHandler.unlockCache.containsKey(player.getUniqueID().toString())))
					{
						unlocked = EventHandler.unlockCache.containsKey(player.getCommandSenderName())? EventHandler.unlockCache.get(player.getCommandSenderName()) : EventHandler.unlockCache.get(player.getUniqueID().toString());
						if(EventHandler.unlockCache.containsKey(player.getCommandSenderName()))
						{
							EventHandler.unlockCache.put(player.getUniqueID().toString(), unlocked);
							EventHandler.unlockCache.remove(player.getCommandSenderName());
						}
						player.getEntityData().setInteger("INFINITE_INVO_UNLOCKED", unlocked);
					} else
					{
						unlocked = player.getEntityData().getInteger("INFINITE_INVO_UNLOCKED");
						EventHandler.unlockCache.put(player.getUniqueID().toString(), unlocked);
					}
					
					if(unlocked > 0 || !II_Settings.xpUnlock)
					{
						player.addStat(InvoAchievements.unlockFirst, 1);
					}
					
					if(unlocked + II_Settings.unlockedSlots == II_Settings.invoSize || !II_Settings.xpUnlock)
					{
						player.addStat(InvoAchievements.unlockAll, 1);
					}
					
					NBTTagCompound reply = new NBTTagCompound();
					reply.setInteger("ID", 0);
					reply.setString("Player", player.getCommandSenderName());
					reply.setInteger("Unlocked", unlocked);
					reply.setTag("Settings", II_Settings.cachedSettings);
					return new InvoPacket(reply);
				} else if(message.tags.getInteger("ID") == 2) // Experimental
				{
					WorldServer world = MinecraftServer.getServer().worldServerForDimension(message.tags.getInteger("World"));
					
					if(world == null)
					{
						InfiniteInvo.logger.log(Level.WARN, "Custom Invo Sync Failed! Reason: Unabled to locate dimension " + message.tags.getInteger("World"));
						return null;
					}
					
					EntityPlayer player = world.getPlayerEntityByName(message.tags.getString("Player"));
					int scrollPos = message.tags.getInteger("Scroll");
					int[] indexes = message.tags.getIntArray("Indexes");
					int[] numbers = message.tags.getIntArray("Numbers");
					boolean resetSlots = message.tags.getBoolean("Reset");
					
					if(player == null || player.getEntityData() == null)
					{
						InfiniteInvo.logger.log(Level.WARN, "Custom Invo Sync Failed! Reason: Unabled to get data for player '" + message.tags.getString("Player") + "'");
						return null;
					}
					
					Container container = player.openContainer;
					
					for(int i = 0; i < numbers.length; i++)
					{
						int sNum = numbers[i];
						int sInx = indexes[i];
						
						Slot s = (Slot)container.inventorySlots.get(sNum);
						
						if(s.inventory instanceof InventoryPlayer)
						{
							if(resetSlots)
							{
								if(s.getClass() != Slot.class && s.getClass() != SlotLockable.class)
								{
									InfiniteInvo.logger.log(Level.WARN, "Container " + container.getClass().getSimpleName() + " is not supported by InfiniteInvo! Reason: Custom Slots (" + s.getClass().getSimpleName() + ") are being used!");
									return null;
								} else if(!(s instanceof SlotLockable))
								{
									Slot r = new SlotLockable(s.inventory, sInx + (scrollPos * 9), s.xDisplayPosition, s.yDisplayPosition);
									
									// Replace the local slot with our own tweaked one so that locked slots are handled properly
									container.inventorySlots.set(s.slotNumber, r);
									r.slotNumber = s.slotNumber;
									s = r;
									// Update the item stack listing.
									container.inventoryItemStacks.set(s.slotNumber, r.getStack());
									r.onSlotChanged();
								}
							}
						}
					}
					
					container.detectAndSendChanges();
				}
			}
			return null;
		}
	}
	
	public static class HandleClient implements IMessageHandler<InvoPacket,IMessage>
	{
		@Override
		public IMessage onMessage(InvoPacket message, MessageContext ctx)
		{
			if(message.tags.hasKey("ID"))
			{
				if(message.tags.getInteger("ID") == 0)
				{
					EntityPlayer player = Minecraft.getMinecraft().thePlayer;
					player.getEntityData().setInteger("INFINITE_INVO_UNLOCKED", message.tags.getInteger("Unlocked"));
					
					if(message.tags.hasKey("Settings"))
					{
						InfiniteInvo.logger.log(Level.INFO, "Loading serverside settings...");
						II_Settings.LoadFromTags(message.tags.getCompoundTag("Settings"));
					}
				}
			}
			return null;
		}
		
	}
}
