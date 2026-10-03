package com.aionemu.gameserver.services;

import java.nio.file.Path;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.validation.SchemaFactory;
import javax.xml.transform.stream.StreamSource;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.dataholders.DecomposableItemsCheck;

/** Production JAXB and selection checks, using copied native XML; no live players or database. */
public final class SeasonPassBoxCheck {
	private static int checks;
	private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
	public static void main(String[] args) throws Exception {
		Path data=Path.of("game-server/data/static_data/decomposable_items");
		SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(data.resolve("decomposable_items.xsd").toFile())
			.newValidator().validate(new StreamSource(data.resolve("decomposable_items.xml").toFile()));
		check(true,"whole authoritative box file passes its native schema");
		Path fixture=Path.of("output/season-pass/box-check");
		DataManager.ITEM_DATA=(ItemData)JAXBContext.newInstance(ItemData.class).createUnmarshaller().unmarshal(fixture.resolve("items.xml").toFile());
		var boxes=(DecomposableItemsData)JAXBContext.newInstance(DecomposableItemsData.class).createUnmarshaller().unmarshal(fixture.resolve("boxes.xml").toFile());
		PlayerClass[] classes={PlayerClass.GLADIATOR,PlayerClass.TEMPLAR,PlayerClass.ASSASSIN,PlayerClass.RANGER,PlayerClass.SORCERER,
			PlayerClass.SPIRIT_MASTER,PlayerClass.CLERIC,PlayerClass.CHANTER,PlayerClass.GUNNER,PlayerClass.RIDER,PlayerClass.BARD};
		for(int id:new int[]{188052187,188052555,188053068,188053975,188053976,188053979,188053980,188053981,188053982,
			188053983,188053984,188053985,188053986,188053987,188053988,188053989}) {
			var info=boxes.getInfoByItemId(id); check(info!=null&&!info.getSets().isEmpty(),"restored pool loaded");
			check(!info.isSelectable(),"original box uses native random opening");
			var roles=id>=188053979?List.of(classes[id-188053979]):List.of(classes);
			for(var pc:roles) for(var race:List.of(Race.ELYOS,Race.ASMODIANS)) {
				var player=DecomposableItemsCheck.player(race,pc,65);
				var groups=info.getSets().stream().filter(g->g.isApplicableTo(player)).toList();
				check(groups.size()==1,"one eligible native reward branch");
				for(var group:groups) {
					for(var reward:group.getRewards()) {
						check(reward.getItems().size()==1,"one award in each native alternative");
						for(var item:reward.getItems()) check(item.getMinimumCount()==item.getMaximumCount(),"fixed published quantities");
					}
				}
				for(int n=0;n<32;n++) {
					var chosen=info.decompose(player);
					check(chosen.size()==1,"selected outcome always grants one eligible award");
				}
			}
		}
		for(int id:new int[]{188053975,188053976}) {
			for(var pc:List.of(PlayerClass.WARRIOR,PlayerClass.SCOUT,PlayerClass.MAGE,PlayerClass.PRIEST,PlayerClass.ENGINEER,PlayerClass.ARTIST))
				check(boxes.getInfoByItemId(id).decompose(DecomposableItemsCheck.player(Race.ELYOS,pc,65)).isEmpty(),"base classes receive no wrong-class bundle");
		}
		System.out.println("OK: "+checks+" native schema/JAXB/weighted-opening checks; all restored boxes loaded. No live player or database changed.");
	}
}
