package us.hoto.mtconnect;

import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Mod(modid = MinecraftTelegramConnect.MODID, name = MinecraftTelegramConnect.NAME, version = MinecraftTelegramConnect.VERSION, acceptableRemoteVersions = "*", serverSideOnly = true)
public class MinecraftTelegramConnect {
	public static final String MODID = "mtconnect";
	public static final String NAME = "Minecraft-Telegram Connect";
	public static final String VERSION = "1.0";

	private static boolean active = true;

	private static Logger logger;

	private static TelegramBot bot;
	private static int updateId = 0;
	private static String token;
	private static Long chatId;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		logger = event.getModLog();
		File file = new File(String.format("config/%s.cfg", MODID));
		try {
			Configuration configuration = new Configuration(file);
			token = configuration.get("telegram", "token", "").getString();
			chatId = configuration.get("telegram", "chatId", "0").getLong();
			if(token.isEmpty() || chatId == 0) {
				try {
					FileUtils.writeStringToFile(file, "telegram {\n\ttoken=\n\tchatId=\n}\n", "UTF-8");
				} catch (Exception e) {
					logger.error("MTConnect failed to init configuration.");
					logger.error(ExceptionUtils.getStackTrace(e));
				}
				logger.warn("MTConnect configure invalid.");
				active = false;
			}
		} catch(Exception e) {
			logger.error("MTConnect failed to load. Maybe your configuration problem.");
			logger.error(ExceptionUtils.getStackTrace(e));
			active = false;
		}
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		logger.info("FML Init");
		if(active) {
			bot = new TelegramBot(token);
			MinecraftForge.EVENT_BUS.register(new MinecraftTelegramConnectEventHandler(bot, chatId));
			getUpdate();
		}
	}

	private void getUpdate() {
		bot.execute(new GetUpdates().limit(100).offset(updateId).timeout(0), new Callback<GetUpdates, GetUpdatesResponse>() {
			@Override
			public void onResponse(GetUpdates request, GetUpdatesResponse response) {
				List<Update> updates = response.updates();
				if(updates != null) {
					if(updates.size() > 0) updateId = updates.get(updates.size() - 1).updateId() + 1;
					updates.forEach(update -> {
						if(update.message() == null) return;
						if(!update.message().chat().id().equals(chatId)) return;
						String name = update.message().from().lastName() == null ? update.message().from().firstName() : String.format("%s %s", update.message().from().firstName(), update.message().from().lastName());
						String text = update.message().text() != null ? update.message().text() : update.message().caption() != null ? update.message().caption() : "(No text in message)";
						FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().sendMessage(new TextComponentString(String.format("[Telegram] <%s> %s", name, text)));
						if(text.equals("mcpl")) {
							bot.execute(new SendMessage(chatId, String.join(",", FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getOnlinePlayerNames())), new Callback() {
								@Override
								public void onResponse(BaseRequest request, BaseResponse response) {

								}
								@Override
								public void onFailure(BaseRequest request, IOException e) {

								}
							});
						}
					});
				}
				getUpdate();
			}

			@Override
			public void onFailure(GetUpdates request, IOException e) {
				getUpdate();
			}
		});
	}
}

class MinecraftTelegramConnectEventHandler {
	private TelegramBot bot;
	private Long chatId;

	public MinecraftTelegramConnectEventHandler(TelegramBot bot, Long chatId) {
		this.bot = bot;
		this.chatId = chatId;
	}

	@SubscribeEvent
	public void serverChat(ServerChatEvent event) {
		bot.execute(new SendMessage(chatId, String.format("<%s> %s", event.getPlayer().getDisplayNameString(), event.getMessage())), new Callback() {
			@Override
			public void onResponse(BaseRequest request, BaseResponse response) {

			}
			@Override
			public void onFailure(BaseRequest request, IOException e) {

			}
		});
	}
}
