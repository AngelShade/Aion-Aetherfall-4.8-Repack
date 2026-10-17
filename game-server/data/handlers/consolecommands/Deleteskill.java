package consolecommands;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.xml.bind.annotation.*;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.skill.PlayerSkillEntry;
import com.aionemu.gameserver.services.SkillLearnService;
import com.aionemu.gameserver.utils.chathandlers.ConsoleCommand;
import com.aionemu.gameserver.utils.xml.JAXBUtil;

/** GM Panel skill removal; native skill/effect removal and stigma custody remain authoritative. */
public class Deleteskill extends ConsoleCommand {

    public Deleteskill() {
        super("deleteskill", "Remove a learned skill from the selected player, or yourself when no player is selected.",
            """
            <skill ID|name> - Remove one skill; accepts GM Panel internal names and full displayed names.
            all - Show how many normal skills can be removed.
            all confirm - Remove normal skills, preserving stigmas, professions and standard actions.
            """);
    }

    @Override
    public void execute(Player admin, String... params) {
        if (params.length == 0) {
            sendInfo(admin);
            return;
        }
        Player player = admin.getTarget() instanceof Player target ? target : admin;
        String requested = String.join(" ", params).trim();
        if (requested.isEmpty()) {
            sendInfo(admin);
            return;
        }
        if (requested.equalsIgnoreCase("all") || requested.equalsIgnoreCase("all confirm")) {
            var skills = List.copyOf(player.getSkillList().getAllSkills()).stream()
                .filter(PlayerSkillEntry::isNormalSkill)
                .filter(entry -> DataManager.SKILL_DATA.getSkillTemplate(entry.getSkillId()) != null).toList();
            if (requested.equalsIgnoreCase("all")) {
                sendInfo(admin, player.getName() + " has " + skills.size()
                    + " removable normal skills. Use deleteskill all confirm to remove them. Stigmas and professions are preserved.");
                return;
            }
            int removed = 0;
            for (var skill : skills)
                if (SkillLearnService.removeSkill(player, skill.getSkillId()))
                    removed++;
            sendInfo(admin, "Removed " + removed + " normal skills from " + player.getName()
                + ". Stigmas, professions and standard actions were preserved.");
            return;
        }
        Integer skillId = resolve(admin, player, requested);
        if (skillId == null)
            return;
        var template = DataManager.SKILL_DATA.getSkillTemplate(skillId);
        if (template == null) {
            sendInfo(admin, "Unknown skill ID: " + skillId + ".");
            return;
        }
        var learned = player.getSkillList().getSkillEntry(skillId);
        if (learned == null) {
            sendInfo(admin, player.getName() + " has not learned " + template.getName() + " (" + skillId + ").");
            return;
        }
        if (learned.isStigmaSkill()) {
            sendInfo(admin, "Remove " + template.getName() + " through the stigma system so its equipped stone and linked skills stay consistent.");
            return;
        }
        if (SkillLearnService.removeSkill(player, skillId))
            sendInfo(admin, "Removed " + template.getName() + " (" + skillId + ") from " + player.getName() + ".");
        else
            sendInfo(admin, player.getName() + " no longer has that skill.");
    }

    private Integer resolve(Player admin, Player player, String requested) {
        if (requested.matches("[0-9]+")) {
            try {
                return Integer.valueOf(requested);
            } catch (NumberFormatException e) {
                sendInfo(admin, "Invalid skill ID: " + requested + ".");
                return null;
            }
        }
        // Keep the client's existing internal skill-name protocol intact.
        Integer internal = PanelNames.BY_NAME.get(requested.toLowerCase(Locale.ROOT));
        if (internal != null)
            return internal;
        var matches = List.copyOf(player.getSkillList().getAllSkills()).stream()
            .map(entry -> DataManager.SKILL_DATA.getSkillTemplate(entry.getSkillId()))
            .filter(template -> template != null && requested.equalsIgnoreCase(template.getName()))
            .sorted(java.util.Comparator.comparingInt(template -> template.getSkillId())).toList();
        if (matches.size() == 1)
            return matches.getFirst().getSkillId();
        if (matches.size() > 1)
            sendInfo(admin, "Several learned skills match that name. Specify a skill ID: "
                + matches.stream().limit(12).map(template -> Integer.toString(template.getSkillId())).collect(Collectors.joining(", ")) + ".");
        else
            sendInfo(admin, "No learned skill named '" + requested + "' on " + player.getName()
                + ". Use its skill ID or the exact GM Panel name.");
        return null;
    }

    private static class PanelNames {
        static final Map<String, Integer> BY_NAME = JAXBUtil.deserialize(
            new File("./data/handlers/consolecommands/data/skills.xml"), SkillData.class).skills.stream()
            .collect(Collectors.toUnmodifiableMap(skill -> skill.name.toLowerCase(Locale.ROOT), skill -> skill.id));
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private static class PanelSkill {
        @XmlAttribute(name = "id", required = true)
        private int id;
        @XmlAttribute(name = "name", required = true)
        private String name;
    }

    @XmlRootElement(name = "skills")
    @XmlAccessorType(XmlAccessType.FIELD)
    private static class SkillData {
        @XmlElement(name = "skill")
        private List<PanelSkill> skills;
    }
}
