package net.minecraft.network;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ServerLoginPacketListener;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatusPacketListener;
import net.minecraft.network.protocol.status.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.util.VisibleForDebug;
import org.slf4j.Logger;

public enum ConnectionProtocol {
    HANDSHAKING(-1, protocol().addFlow(PacketFlow.SERVERBOUND, (new ConnectionProtocol.PacketSet<ServerHandshakePacketListener>()).addPacket(ClientIntentionPacket.class, ClientIntentionPacket::new))),
    PLAY(0, protocol().addFlow(PacketFlow.CLIENTBOUND, (new ConnectionProtocol.PacketSet<ClientGamePacketListener>()).addPacket(ClientboundAddEntityPacket.class, ClientboundAddEntityPacket::new).addPacket(ClientboundAddExperienceOrbPacket.class, ClientboundAddExperienceOrbPacket::new).addPacket(ClientboundAddPlayerPacket.class, ClientboundAddPlayerPacket::new).addPacket(ClientboundAnimatePacket.class, ClientboundAnimatePacket::new).addPacket(ClientboundAwardStatsPacket.class, ClientboundAwardStatsPacket::new).addPacket(ClientboundBlockChangedAckPacket.class, ClientboundBlockChangedAckPacket::new).addPacket(ClientboundBlockDestructionPacket.class, ClientboundBlockDestructionPacket::new).addPacket(ClientboundBlockEntityDataPacket.class, ClientboundBlockEntityDataPacket::new).addPacket(ClientboundBlockEventPacket.class, ClientboundBlockEventPacket::new).addPacket(ClientboundBlockUpdatePacket.class, ClientboundBlockUpdatePacket::new).addPacket(ClientboundBossEventPacket.class, ClientboundBossEventPacket::new).addPacket(ClientboundChangeDifficultyPacket.class, ClientboundChangeDifficultyPacket::new).addPacket(ClientboundClearTitlesPacket.class, ClientboundClearTitlesPacket::new).addPacket(ClientboundCommandSuggestionsPacket.class, ClientboundCommandSuggestionsPacket::new).addPacket(ClientboundCommandsPacket.class, ClientboundCommandsPacket::new).addPacket(ClientboundContainerClosePacket.class, ClientboundContainerClosePacket::new).addPacket(ClientboundContainerSetContentPacket.class, ClientboundContainerSetContentPacket::new).addPacket(ClientboundContainerSetDataPacket.class, ClientboundContainerSetDataPacket::new).addPacket(ClientboundContainerSetSlotPacket.class, ClientboundContainerSetSlotPacket::new).addPacket(ClientboundCooldownPacket.class, ClientboundCooldownPacket::new).addPacket(ClientboundCustomChatCompletionsPacket.class, ClientboundCustomChatCompletionsPacket::new).addPacket(ClientboundCustomPayloadPacket.class, ClientboundCustomPayloadPacket::new).addPacket(ClientboundDeleteChatPacket.class, ClientboundDeleteChatPacket::new).addPacket(ClientboundDisconnectPacket.class, ClientboundDisconnectPacket::new).addPacket(ClientboundDisguisedChatPacket.class, ClientboundDisguisedChatPacket::new).addPacket(ClientboundEntityEventPacket.class, ClientboundEntityEventPacket::new).addPacket(ClientboundExplodePacket.class, ClientboundExplodePacket::new).addPacket(ClientboundForgetLevelChunkPacket.class, ClientboundForgetLevelChunkPacket::new).addPacket(ClientboundGameEventPacket.class, ClientboundGameEventPacket::new).addPacket(ClientboundHorseScreenOpenPacket.class, ClientboundHorseScreenOpenPacket::new).addPacket(ClientboundInitializeBorderPacket.class, ClientboundInitializeBorderPacket::new).addPacket(ClientboundKeepAlivePacket.class, ClientboundKeepAlivePacket::new).addPacket(ClientboundLevelChunkWithLightPacket.class, ClientboundLevelChunkWithLightPacket::new).addPacket(ClientboundLevelEventPacket.class, ClientboundLevelEventPacket::new).addPacket(ClientboundLevelParticlesPacket.class, ClientboundLevelParticlesPacket::new).addPacket(ClientboundLightUpdatePacket.class, ClientboundLightUpdatePacket::new).addPacket(ClientboundLoginPacket.class, ClientboundLoginPacket::new).addPacket(ClientboundMapItemDataPacket.class, ClientboundMapItemDataPacket::new).addPacket(ClientboundMerchantOffersPacket.class, ClientboundMerchantOffersPacket::new).addPacket(ClientboundMoveEntityPacket.Pos.class, ClientboundMoveEntityPacket.Pos::read).addPacket(ClientboundMoveEntityPacket.PosRot.class, ClientboundMoveEntityPacket.PosRot::read).addPacket(ClientboundMoveEntityPacket.Rot.class, ClientboundMoveEntityPacket.Rot::read).addPacket(ClientboundMoveVehiclePacket.class, ClientboundMoveVehiclePacket::new).addPacket(ClientboundOpenBookPacket.class, ClientboundOpenBookPacket::new).addPacket(ClientboundOpenScreenPacket.class, ClientboundOpenScreenPacket::new).addPacket(ClientboundOpenSignEditorPacket.class, ClientboundOpenSignEditorPacket::new).addPacket(ClientboundPingPacket.class, ClientboundPingPacket::new).addPacket(ClientboundPlaceGhostRecipePacket.class, ClientboundPlaceGhostRecipePacket::new).addPacket(ClientboundPlayerAbilitiesPacket.class, ClientboundPlayerAbilitiesPacket::new).addPacket(ClientboundPlayerChatPacket.class, ClientboundPlayerChatPacket::new).addPacket(ClientboundPlayerCombatEndPacket.class, ClientboundPlayerCombatEndPacket::new).addPacket(ClientboundPlayerCombatEnterPacket.class, ClientboundPlayerCombatEnterPacket::new).addPacket(ClientboundPlayerCombatKillPacket.class, ClientboundPlayerCombatKillPacket::new).addPacket(ClientboundPlayerInfoRemovePacket.class, ClientboundPlayerInfoRemovePacket::new).addPacket(ClientboundPlayerInfoUpdatePacket.class, ClientboundPlayerInfoUpdatePacket::new).addPacket(ClientboundPlayerLookAtPacket.class, ClientboundPlayerLookAtPacket::new).addPacket(ClientboundPlayerPositionPacket.class, ClientboundPlayerPositionPacket::new).addPacket(ClientboundRecipePacket.class, ClientboundRecipePacket::new).addPacket(ClientboundRemoveEntitiesPacket.class, ClientboundRemoveEntitiesPacket::new).addPacket(ClientboundRemoveMobEffectPacket.class, ClientboundRemoveMobEffectPacket::new).addPacket(ClientboundResourcePackPacket.class, ClientboundResourcePackPacket::new).addPacket(ClientboundRespawnPacket.class, ClientboundRespawnPacket::new).addPacket(ClientboundRotateHeadPacket.class, ClientboundRotateHeadPacket::new).addPacket(ClientboundSectionBlocksUpdatePacket.class, ClientboundSectionBlocksUpdatePacket::new).addPacket(ClientboundSelectAdvancementsTabPacket.class, ClientboundSelectAdvancementsTabPacket::new).addPacket(ClientboundServerDataPacket.class, ClientboundServerDataPacket::new).addPacket(ClientboundSetActionBarTextPacket.class, ClientboundSetActionBarTextPacket::new).addPacket(ClientboundSetBorderCenterPacket.class, ClientboundSetBorderCenterPacket::new).addPacket(ClientboundSetBorderLerpSizePacket.class, ClientboundSetBorderLerpSizePacket::new).addPacket(ClientboundSetBorderSizePacket.class, ClientboundSetBorderSizePacket::new).addPacket(ClientboundSetBorderWarningDelayPacket.class, ClientboundSetBorderWarningDelayPacket::new).addPacket(ClientboundSetBorderWarningDistancePacket.class, ClientboundSetBorderWarningDistancePacket::new).addPacket(ClientboundSetCameraPacket.class, ClientboundSetCameraPacket::new).addPacket(ClientboundSetCarriedItemPacket.class, ClientboundSetCarriedItemPacket::new).addPacket(ClientboundSetChunkCacheCenterPacket.class, ClientboundSetChunkCacheCenterPacket::new).addPacket(ClientboundSetChunkCacheRadiusPacket.class, ClientboundSetChunkCacheRadiusPacket::new).addPacket(ClientboundSetDefaultSpawnPositionPacket.class, ClientboundSetDefaultSpawnPositionPacket::new).addPacket(ClientboundSetDisplayObjectivePacket.class, ClientboundSetDisplayObjectivePacket::new).addPacket(ClientboundSetEntityDataPacket.class, ClientboundSetEntityDataPacket::new).addPacket(ClientboundSetEntityLinkPacket.class, ClientboundSetEntityLinkPacket::new).addPacket(ClientboundSetEntityMotionPacket.class, ClientboundSetEntityMotionPacket::new).addPacket(ClientboundSetEquipmentPacket.class, ClientboundSetEquipmentPacket::new).addPacket(ClientboundSetExperiencePacket.class, ClientboundSetExperiencePacket::new).addPacket(ClientboundSetHealthPacket.class, ClientboundSetHealthPacket::new).addPacket(ClientboundSetObjectivePacket.class, ClientboundSetObjectivePacket::new).addPacket(ClientboundSetPassengersPacket.class, ClientboundSetPassengersPacket::new).addPacket(ClientboundSetPlayerTeamPacket.class, ClientboundSetPlayerTeamPacket::new).addPacket(ClientboundSetScorePacket.class, ClientboundSetScorePacket::new).addPacket(ClientboundSetSimulationDistancePacket.class, ClientboundSetSimulationDistancePacket::new).addPacket(ClientboundSetSubtitleTextPacket.class, ClientboundSetSubtitleTextPacket::new).addPacket(ClientboundSetTimePacket.class, ClientboundSetTimePacket::new).addPacket(ClientboundSetTitleTextPacket.class, ClientboundSetTitleTextPacket::new).addPacket(ClientboundSetTitlesAnimationPacket.class, ClientboundSetTitlesAnimationPacket::new).addPacket(ClientboundSoundEntityPacket.class, ClientboundSoundEntityPacket::new).addPacket(ClientboundSoundPacket.class, ClientboundSoundPacket::new).addPacket(ClientboundStopSoundPacket.class, ClientboundStopSoundPacket::new).addPacket(ClientboundSystemChatPacket.class, ClientboundSystemChatPacket::new).addPacket(ClientboundTabListPacket.class, ClientboundTabListPacket::new).addPacket(ClientboundTagQueryPacket.class, ClientboundTagQueryPacket::new).addPacket(ClientboundTakeItemEntityPacket.class, ClientboundTakeItemEntityPacket::new).addPacket(ClientboundTeleportEntityPacket.class, ClientboundTeleportEntityPacket::new).addPacket(ClientboundUpdateAdvancementsPacket.class, ClientboundUpdateAdvancementsPacket::new).addPacket(ClientboundUpdateAttributesPacket.class, ClientboundUpdateAttributesPacket::new).addPacket(ClientboundUpdateEnabledFeaturesPacket.class, ClientboundUpdateEnabledFeaturesPacket::new).addPacket(ClientboundUpdateMobEffectPacket.class, ClientboundUpdateMobEffectPacket::new).addPacket(ClientboundUpdateRecipesPacket.class, ClientboundUpdateRecipesPacket::new).addPacket(ClientboundUpdateTagsPacket.class, ClientboundUpdateTagsPacket::new)).addFlow(PacketFlow.SERVERBOUND, (new ConnectionProtocol.PacketSet<ServerGamePacketListener>()).addPacket(ServerboundAcceptTeleportationPacket.class, ServerboundAcceptTeleportationPacket::new).addPacket(ServerboundBlockEntityTagQuery.class, ServerboundBlockEntityTagQuery::new).addPacket(ServerboundChangeDifficultyPacket.class, ServerboundChangeDifficultyPacket::new).addPacket(ServerboundChatAckPacket.class, ServerboundChatAckPacket::new).addPacket(ServerboundChatCommandPacket.class, ServerboundChatCommandPacket::new).addPacket(ServerboundChatPacket.class, ServerboundChatPacket::new).addPacket(ServerboundClientCommandPacket.class, ServerboundClientCommandPacket::new).addPacket(ServerboundClientInformationPacket.class, ServerboundClientInformationPacket::new).addPacket(ServerboundCommandSuggestionPacket.class, ServerboundCommandSuggestionPacket::new).addPacket(ServerboundContainerButtonClickPacket.class, ServerboundContainerButtonClickPacket::new).addPacket(ServerboundContainerClickPacket.class, ServerboundContainerClickPacket::new).addPacket(ServerboundContainerClosePacket.class, ServerboundContainerClosePacket::new).addPacket(ServerboundCustomPayloadPacket.class, ServerboundCustomPayloadPacket::new).addPacket(ServerboundEditBookPacket.class, ServerboundEditBookPacket::new).addPacket(ServerboundEntityTagQuery.class, ServerboundEntityTagQuery::new).addPacket(ServerboundInteractPacket.class, ServerboundInteractPacket::new).addPacket(ServerboundJigsawGeneratePacket.class, ServerboundJigsawGeneratePacket::new).addPacket(ServerboundKeepAlivePacket.class, ServerboundKeepAlivePacket::new).addPacket(ServerboundLockDifficultyPacket.class, ServerboundLockDifficultyPacket::new).addPacket(ServerboundMovePlayerPacket.Pos.class, ServerboundMovePlayerPacket.Pos::read).addPacket(ServerboundMovePlayerPacket.PosRot.class, ServerboundMovePlayerPacket.PosRot::read).addPacket(ServerboundMovePlayerPacket.Rot.class, ServerboundMovePlayerPacket.Rot::read).addPacket(ServerboundMovePlayerPacket.StatusOnly.class, ServerboundMovePlayerPacket.StatusOnly::read).addPacket(ServerboundMoveVehiclePacket.class, ServerboundMoveVehiclePacket::new).addPacket(ServerboundPaddleBoatPacket.class, ServerboundPaddleBoatPacket::new).addPacket(ServerboundPickItemPacket.class, ServerboundPickItemPacket::new).addPacket(ServerboundPlaceRecipePacket.class, ServerboundPlaceRecipePacket::new).addPacket(ServerboundPlayerAbilitiesPacket.class, ServerboundPlayerAbilitiesPacket::new).addPacket(ServerboundPlayerActionPacket.class, ServerboundPlayerActionPacket::new).addPacket(ServerboundPlayerCommandPacket.class, ServerboundPlayerCommandPacket::new).addPacket(ServerboundPlayerInputPacket.class, ServerboundPlayerInputPacket::new).addPacket(ServerboundPongPacket.class, ServerboundPongPacket::new).addPacket(ServerboundChatSessionUpdatePacket.class, ServerboundChatSessionUpdatePacket::new).addPacket(ServerboundRecipeBookChangeSettingsPacket.class, ServerboundRecipeBookChangeSettingsPacket::new).addPacket(ServerboundRecipeBookSeenRecipePacket.class, ServerboundRecipeBookSeenRecipePacket::new).addPacket(ServerboundRenameItemPacket.class, ServerboundRenameItemPacket::new).addPacket(ServerboundResourcePackPacket.class, ServerboundResourcePackPacket::new).addPacket(ServerboundSeenAdvancementsPacket.class, ServerboundSeenAdvancementsPacket::new).addPacket(ServerboundSelectTradePacket.class, ServerboundSelectTradePacket::new).addPacket(ServerboundSetBeaconPacket.class, ServerboundSetBeaconPacket::new).addPacket(ServerboundSetCarriedItemPacket.class, ServerboundSetCarriedItemPacket::new).addPacket(ServerboundSetCommandBlockPacket.class, ServerboundSetCommandBlockPacket::new).addPacket(ServerboundSetCommandMinecartPacket.class, ServerboundSetCommandMinecartPacket::new).addPacket(ServerboundSetCreativeModeSlotPacket.class, ServerboundSetCreativeModeSlotPacket::new).addPacket(ServerboundSetJigsawBlockPacket.class, ServerboundSetJigsawBlockPacket::new).addPacket(ServerboundSetStructureBlockPacket.class, ServerboundSetStructureBlockPacket::new).addPacket(ServerboundSignUpdatePacket.class, ServerboundSignUpdatePacket::new).addPacket(ServerboundSwingPacket.class, ServerboundSwingPacket::new).addPacket(ServerboundTeleportToEntityPacket.class, ServerboundTeleportToEntityPacket::new).addPacket(ServerboundUseItemOnPacket.class, ServerboundUseItemOnPacket::new).addPacket(ServerboundUseItemPacket.class, ServerboundUseItemPacket::new))),
    STATUS(1, protocol().addFlow(PacketFlow.SERVERBOUND, (new ConnectionProtocol.PacketSet<ServerStatusPacketListener>()).addPacket(ServerboundStatusRequestPacket.class, ServerboundStatusRequestPacket::new).addPacket(ServerboundPingRequestPacket.class, ServerboundPingRequestPacket::new)).addFlow(PacketFlow.CLIENTBOUND, (new ConnectionProtocol.PacketSet<ClientStatusPacketListener>()).addPacket(ClientboundStatusResponsePacket.class, ClientboundStatusResponsePacket::new).addPacket(ClientboundPongResponsePacket.class, ClientboundPongResponsePacket::new))),
    LOGIN(2, protocol().addFlow(PacketFlow.CLIENTBOUND, (new ConnectionProtocol.PacketSet<ClientLoginPacketListener>()).addPacket(ClientboundLoginDisconnectPacket.class, ClientboundLoginDisconnectPacket::new).addPacket(ClientboundHelloPacket.class, ClientboundHelloPacket::new).addPacket(ClientboundGameProfilePacket.class, ClientboundGameProfilePacket::new).addPacket(ClientboundLoginCompressionPacket.class, ClientboundLoginCompressionPacket::new).addPacket(ClientboundCustomQueryPacket.class, ClientboundCustomQueryPacket::new)).addFlow(PacketFlow.SERVERBOUND, (new ConnectionProtocol.PacketSet<ServerLoginPacketListener>()).addPacket(ServerboundHelloPacket.class, ServerboundHelloPacket::new).addPacket(ServerboundKeyPacket.class, ServerboundKeyPacket::new).addPacket(ServerboundCustomQueryPacket.class, ServerboundCustomQueryPacket::new)));

    private static final int MIN_PROTOCOL_ID = -1;
    private static final int MAX_PROTOCOL_ID = 2;
    private static final ConnectionProtocol[] LOOKUP = new ConnectionProtocol[4];
    private static final Map<Class<? extends Packet<?>>, ConnectionProtocol> PROTOCOL_BY_PACKET = Maps.newHashMap();
    private final int id;
    private final Map<PacketFlow, ? extends ConnectionProtocol.PacketSet<?>> flows;

    private static ConnectionProtocol.ProtocolBuilder protocol() {
        return new ConnectionProtocol.ProtocolBuilder();
    }

    private ConnectionProtocol(int id, ConnectionProtocol.ProtocolBuilder initializer) {
        this.id = id;
        this.flows = initializer.flows;
    }

    @Nullable
    public Integer getPacketId(PacketFlow side, Packet<?> packet) {
        return this.flows.get(side).getId(packet.getClass());
    }

    @VisibleForDebug
    public Int2ObjectMap<Class<? extends Packet<?>>> getPacketsByIds(PacketFlow side) {
        Int2ObjectMap<Class<? extends Packet<?>>> int2ObjectMap = new Int2ObjectOpenHashMap<>();
        ConnectionProtocol.PacketSet<?> packetSet = this.flows.get(side);
        if (packetSet == null) {
            return Int2ObjectMaps.emptyMap();
        } else {
            packetSet.classToId.forEach((packetId, integer) -> {
                int2ObjectMap.put(integer.intValue(), packetId);
            });
            return int2ObjectMap;
        }
    }

    @Nullable
    public Packet<?> createPacket(PacketFlow side, int packetId, FriendlyByteBuf buf) {
        return this.flows.get(side).createPacket(packetId, buf);
    }

    public int getId() {
        return this.id;
    }

    @Nullable
    public static ConnectionProtocol getById(int id) {
        return id >= -1 && id <= 2 ? LOOKUP[id - -1] : null;
    }

    public static ConnectionProtocol getProtocolForPacket(Packet<?> handler) {
        return PROTOCOL_BY_PACKET.get(handler.getClass());
    }

    static {
        for(ConnectionProtocol connectionProtocol : values()) {
            int i = connectionProtocol.getId();
            if (i < -1 || i > 2) {
                throw new Error("Invalid protocol ID " + i);
            }

            LOOKUP[i - -1] = connectionProtocol;
            connectionProtocol.flows.forEach((side, handler) -> {
                handler.getAllPackets().forEach((packetClass) -> {
                    if (PROTOCOL_BY_PACKET.containsKey(packetClass) && PROTOCOL_BY_PACKET.get(packetClass) != connectionProtocol) {
                        throw new IllegalStateException("Packet " + packetClass + " is already assigned to protocol " + PROTOCOL_BY_PACKET.get(packetClass) + " - can't reassign to " + connectionProtocol);
                    } else {
                        PROTOCOL_BY_PACKET.put(packetClass, connectionProtocol);
                    }
                });
            });
        }

    }

    static class PacketSet<T extends PacketListener> {
        private static final Logger LOGGER = LogUtils.getLogger();
        final Object2IntMap<Class<? extends Packet<T>>> classToId = Util.make(new Object2IntOpenHashMap<>(), (map) -> {
            map.defaultReturnValue(-1);
        });
        private final List<Function<FriendlyByteBuf, ? extends Packet<T>>> idToDeserializer = Lists.newArrayList();

        public <P extends Packet<T>> ConnectionProtocol.PacketSet<T> addPacket(Class<P> type, Function<FriendlyByteBuf, P> packetFactory) {
            int i = this.idToDeserializer.size();
            int j = this.classToId.put(type, i);
            if (j != -1) {
                String string = "Packet " + type + " is already registered to ID " + j;
                LOGGER.error(LogUtils.FATAL_MARKER, string);
                throw new IllegalArgumentException(string);
            } else {
                this.idToDeserializer.add(packetFactory);
                return this;
            }
        }

        @Nullable
        public Integer getId(Class<?> packet) {
            int i = this.classToId.getInt(packet);
            return i == -1 ? null : i;
        }

        @Nullable
        public Packet<?> createPacket(int id, FriendlyByteBuf buf) {
            if (id < 0 || id >= this.idToDeserializer.size()) return null; // Paper
            Function<FriendlyByteBuf, ? extends Packet<T>> function = this.idToDeserializer.get(id);
            return function != null ? function.apply(buf) : null;
        }

        public Iterable<Class<? extends Packet<?>>> getAllPackets() {
            return Iterables.unmodifiableIterable(this.classToId.keySet());
        }
    }

    static class ProtocolBuilder {
        final Map<PacketFlow, ConnectionProtocol.PacketSet<?>> flows = Maps.newEnumMap(PacketFlow.class);

        public <T extends PacketListener> ConnectionProtocol.ProtocolBuilder addFlow(PacketFlow side, ConnectionProtocol.PacketSet<T> handler) {
            this.flows.put(side, handler);
            return this;
        }
    }
}
