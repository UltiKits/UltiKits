package com.ultikits.plugins.kits.service;

import com.ultikits.plugins.kits.config.KitsConfig;
import com.ultikits.plugins.kits.entity.KitClaimData;
import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.utils.EconomyUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of KitService.
 * 礼包服务实现。
 */
@Service
public class KitServiceImpl implements KitService {

    private final UltiToolsPlugin plugin;
    /**
     * The module's live configuration object, not a snapshot of its values. {@code ConfigManager}
     * re-reads the file into this same instance on {@code /ul reload}, so {@link #claimKit} reads
     * {@code enabled} at the moment a claim is attempted and a flip applies to the very next one.
     * <p>
     * 模块的实时配置对象（而非取值快照）；{@code /ul reload} 会把文件重新读入同一个实例。
     */
    private final KitsConfig config;
    private final PluginLogger logger;
    private final Map<String, KitDefinition> kits = new LinkedHashMap<>();
    /**
     * Kits whose refused-withdrawal warning has already been written this session, so the console
     * gets one line per kit rather than one per attempt. Claiming is player-triggered behind only
     * the browser's 200ms debounce, and {@code /kits claim} has no cooldown at all, so during an
     * economy outage the unthrottled form buries the log in the window an operator most needs to
     * read it. This follows the framework's own precedent - {@code EconomyUtils} emits exactly one
     * line per calling module per server session for the adjacent condition. Bounded by the size
     * of the kit catalogue, and cleared by {@link #loadKits()} so a reload re-arms it.
     * <p>
     * 每个礼包每个会话只记录一次扣款被拒的警告，避免经济系统故障时刷屏；重新加载礼包会重置。
     */
    private final Set<String> refusalWarnedKits = Collections.synchronizedSet(new LinkedHashSet<>());
    private DataOperator<KitClaimData> claimOperator;

    public KitServiceImpl(UltiToolsPlugin plugin, KitsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        this.claimOperator = plugin.getDataOperator(KitClaimData.class);
        loadKits();
    }

    @Override
    public void loadKits() {
        kits.clear();
        refusalWarnedKits.clear();

        File kitsFolder = new File(plugin.getResourceFolderPath(), "kits");
        if (!kitsFolder.exists()) {
            kitsFolder.mkdirs();
            copyExampleKit(kitsFolder);
        }

        File[] files = kitsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            logger.warn(plugin.i18n("kits.log.no_kit_files"));
            return;
        }

        int loadedCount = 0;
        Map<String, List<File>> filesByKit = new LinkedHashMap<>();
        Map<String, File> loadedFrom = new HashMap<>();
        for (File file : files) {
            String kitName = kitNameOf(file);
            filesByKit.computeIfAbsent(kitName, name -> new ArrayList<>()).add(file);
            KitDefinition kit = parseKitFile(file);
            if (kit != null) {
                kit.setName(kitName);
                kits.put(kitName, kit);
                loadedFrom.put(kitName, file);
                loadedCount++;
            }
        }
        // Loading is unchanged when several files define one kit (the last one listed wins, as
        // before), but that kit is no longer saved or deleted, so the console names the files.
        for (Map.Entry<String, List<File>> entry : filesByKit.entrySet()) {
            File loaded = loadedFrom.get(entry.getKey());
            if (entry.getValue().size() > 1 && loaded != null) {
                logger.warn(String.format(plugin.i18n("kits.log.kit_file_conflict"),
                        kitsFolder.getAbsolutePath(), entry.getKey(),
                        String.join(", ", fileNames(entry.getValue())), loaded.getName()));
            }
        }

        logger.info(String.format(plugin.i18n("kits.log.loaded_count"), loadedCount));
    }

    @Override
    public void reload() {
        loadKits();
    }

    @Nullable
    @Override
    public KitDefinition getKit(String name) {
        return kits.get(name.toLowerCase());
    }

    @Override
    public Collection<KitDefinition> getAllKits() {
        return Collections.unmodifiableCollection(kits.values());
    }

    @Override
    public List<KitDefinition> getAvailableKits(Player player) {
        return kits.values().stream()
                .filter(kit -> !kit.hasPermission() || player.hasPermission(kit.getPermission()))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getKitNames() {
        return new ArrayList<>(kits.keySet());
    }

    @Override
    public CreateResult createKit(Player player, String name) {
        String normalizedName = name.toLowerCase().trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 32) {
            return CreateResult.INVALID_NAME;
        }

        if (kits.get(normalizedName) != null) {
            return CreateResult.ALREADY_EXISTS;
        }
        // Files that load as this name but did not parse still decide which one the next reload
        // reads, so a name several of them share is refused like a save.
        if (!conflictingFiles(normalizedName).isEmpty()) {
            return CreateResult.FILE_CONFLICT;
        }

        // Filter out air and null items from player inventory
        ItemStack[] validItems = Arrays.stream(player.getInventory().getStorageContents())
                .filter(item -> item != null && item.getType() != Material.AIR)
                .toArray(ItemStack[]::new);

        if (validItems.length == 0) {
            return CreateResult.EMPTY_INVENTORY;
        }

        String serializedItems = serializeItems(validItems);
        if (serializedItems == null) {
            return CreateResult.ERROR;
        }

        // Create kit definition
        KitDefinition kit = new KitDefinition();
        kit.setName(normalizedName);
        kit.setDisplayName("&f" + name);
        kit.setIcon(validItems[0].getType().name());
        kit.setItems(serializedItems);

        // Save to YAML
        if (!saveKitToFile(normalizedName, kit)) {
            // The writer also refuses a second file that appeared after the check above.
            return conflictingFiles(normalizedName).isEmpty() ? CreateResult.ERROR : CreateResult.FILE_CONFLICT;
        }

        kits.put(normalizedName, kit);
        return CreateResult.SUCCESS;
    }

    /**
     * Deletes a kit: its file first, then its catalogue entry, and the catalogue entry only when the
     * file is really gone.
     * <p>
     * {@link #loadKits()} rebuilds the catalogue from the files in the kits folder, so a kit whose
     * file survived a delete comes back on the next {@code /kits reload} or restart. Removing it from
     * the catalogue anyway - which this method used to do, discarding {@link File#delete()}'s result -
     * told the admin a kit was gone that would return, and a kit deleted because it was mispriced or
     * handed out something it should not was claimable again after the reload (UltiKits/UltiKits#23).
     * When the file cannot be removed the kit therefore stays loaded, the result says so, and a
     * console warning names the path so the operator can see which file and fix its permissions.
     * <p>
     * 先删文件，文件确实不在了才从目录中移除礼包；删除失败时保留礼包、返回失败并记录包含路径的警告。
     *
     * @param name the kit's name / 礼包名
     * @return the outcome, never null / 结果，不为 null
     */
    @Override
    public DeleteResult deleteKit(String name) {
        String normalizedName = name.toLowerCase().trim();
        if (kits.get(normalizedName) == null) {
            return DeleteResult.NOT_FOUND;
        }

        // Every file that loads as this kit, found the way loadKits maps files to names - not a
        // rebuilt "<name>.yml", which misses a hand-placed "VIP.yml" on a case-sensitive file system.
        List<File> kitFiles = kitFilesOf(normalizedName);
        if (kitFiles == null) {
            // The folder could not be listed, which is not the same as "no file": the kit's file may
            // still be there and load again on the next reload.
            logger.warn(String.format(plugin.i18n("kits.log.kits_folder_unreadable"), kitsFolder().getAbsolutePath(),
                    normalizedName));
            return DeleteResult.FILE_NOT_DELETED;
        }
        if (kitFiles.size() > 1) {
            // Deleting them one by one can stop part-way and leave a different file to load next
            // time, so none is removed until only one file defines the kit.
            return DeleteResult.FILE_CONFLICT;
        }
        boolean survived = false;
        for (File kitFile : kitFiles) {
            try {
                deleteKitFile(kitFile);
            } catch (NoSuchFileException gone) {
                // Another process removed it first: it is gone, which is what the admin asked for.
            } catch (IOException e) {
                // Any other reason is a failure. The delete reports it; no existence check is asked
                // afterwards, because in a folder the server may list but not search File#exists
                // answers false for a file that is still there.
                logger.warn(String.format(plugin.i18n("kits.log.delete_file_failed"), kitFile.getAbsolutePath()));
                survived = true;
            }
        }
        if (survived) {
            return DeleteResult.FILE_NOT_DELETED;
        }

        kits.remove(normalizedName);
        return DeleteResult.DELETED;
    }

    /**
     * The kit name a file in the kits folder loads as. The one mapping {@link #loadKits()},
     * {@link #deleteKit} and {@link #saveKitToFile} share, so "the kit's file" is decided in one place:
     * rebuilding a path from the kit's name missed a file whose name has capitals.
     * <p>
     * 文件对应的礼包名；加载、删除和保存共用这一映射。
     */
    static String kitNameOf(File file) {
        return file.getName().replace(".yml", "").toLowerCase();
    }

    /**
     * Every {@code .yml} file in the kits folder that loads as {@code kitName}: empty when none does, and
     * {@code null} when the folder could not be listed - a caller must not read a failed scan as "no file".
     */
    @Nullable
    private List<File> kitFilesOf(String kitName) {
        File[] files = listKitFiles(kitsFolder());
        if (files == null) {
            return null;
        }
        List<File> matches = new ArrayList<>();
        for (File file : files) {
            if (kitNameOf(file).equals(kitName)) {
                matches.add(file);
            }
        }
        return matches;
    }

    private File kitsFolder() {
        return new File(plugin.getResourceFolderPath(), "kits");
    }

    /**
     * Lists the kit files in a folder: empty when the folder does not exist, {@code null} when it exists
     * but cannot be listed. Read through {@link Files#newDirectoryStream}, which says why a listing
     * failed, because {@link File#listFiles} answers {@code null} for both - and a missing folder holds
     * no file, while an unreadable one may. A seam, package-private so a test can make the listing fail
     * without depending on file permissions.
     * <p>
     * 列出文件夹中的礼包文件：文件夹不存在时为空，存在但无法读取时为 {@code null}。包级可见，供测试模拟读取失败。
     */
    @Nullable
    File[] listKitFiles(File folder) {
        List<File> files = new ArrayList<>();
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(folder.toPath(), path -> path.getFileName().toString().endsWith(".yml"))) {
            for (Path path : stream) {
                files.add(path.toFile());
            }
        } catch (NoSuchFileException missing) {
            return new File[0];
        } catch (IOException | DirectoryIteratorException e) {
            return null;
        }
        return files.toArray(new File[0]);
    }

    /**
     * Deletes one kit file through {@link Files#delete}, which says why it failed instead of answering
     * {@code false}: {@link NoSuchFileException} when the file is already gone, another
     * {@link IOException} when it could not be removed. A seam, package-private so a test can make the
     * deletion fail: a permission-based test is not one, because a build running as root deletes a
     * read-only file.
     * <p>
     * 通过 {@link Files#delete} 删除单个礼包文件，失败时给出原因；包级可见，供测试模拟删除失败。
     *
     * @param kitFile the file to delete / 要删除的文件
     * @throws IOException when the file could not be deleted / 无法删除时抛出
     */
    void deleteKitFile(File kitFile) throws IOException {
        Files.delete(kitFile.toPath());
    }

    /**
     * Replaces {@code target} with {@code content} so that the file is either the old one or the whole
     * new one: the text is written to a temporary file in the same folder (named so the kit loader,
     * which reads only {@code .yml}, ignores it) and then moved over the target in one step. A file
     * system that does not support an atomic move gets a plain replacing move of the complete file.
     * The temporary file is removed whatever happens. Before, {@link YamlConfiguration#save} truncated
     * the kit file and then wrote it, so a failure part-way left a cut-off file.
     * <p>
     * 先写同目录临时文件，再一次性移动覆盖礼包文件；失败时原文件不变，临时文件总会被清理。
     */
    private void writeAtomically(File target, byte[] content) throws IOException {
        Path targetPath = target.getAbsoluteFile().toPath();
        if (Files.isSymbolicLink(targetPath) && !Files.exists(targetPath)) {
            // A link whose target has gone: replacing it would destroy the operator's link.
            throw new NoSuchFileException(targetPath.toString(), null, "kit file is a symbolic link to a missing file");
        }
        if (Files.exists(targetPath)) {
            // Replace what an in-place write would have written: the file a symbolic link points to,
            // and never a file the server may not write (a rename needs only the folder's permission).
            targetPath = targetPath.toRealPath();
            if (!Files.isWritable(targetPath)) {
                throw new AccessDeniedException(targetPath.toString(), null, "kit file is not writable");
            }
        }
        Path folder = targetPath.getParent();
        Files.createDirectories(folder);
        Path temp = createTempSibling(folder, targetPath.getFileName().toString());
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                // The replaced file's permissions, list and owner go on while the file is still empty,
                // so the content is never readable by anyone the kit file does not let read it. The
                // channel is opened first so a permission set that leaves the server only group access
                // still lets it write.
                copyFileIdentity(targetPath, temp);
                writeContent(temp, channel, content);
                // On disk before the move, so a power loss cannot leave the moved name pointing at an
                // empty file.
                channel.force(true);
            }
            try {
                atomicMove(temp, targetPath);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                logger.warn(String.format(plugin.i18n("kits.log.temp_file_not_removed"), temp, cleanup.getMessage()));
            }
        }
    }

    /**
     * Writes the whole of {@code content} into the open temporary file. A seam, package-private so a
     * test can observe the temporary file at the moment its content is written.
     */
    void writeContent(Path temp, FileChannel channel, byte[] content) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * A new, empty file next to {@code name} in {@code folder}, created without explicit attributes so
     * it gets the same permission bits as any file the server creates there ({@link Files#createTempFile}
     * would make it {@code rw-------}). Its name starts with a dot and ends in {@code .tmp}, so the kit
     * loader, which reads only {@code .yml}, never loads it.
     */
    private static Path createTempSibling(Path folder, String name) throws IOException {
        for (int attempt = 0; ; attempt++) {
            Path candidate = folder.resolve("." + name + "." + System.nanoTime() + "." + attempt + ".tmp");
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException taken) {
                if (attempt >= 9) {
                    throw taken;
                }
            }
        }
    }

    /**
     * Gives the replacement file the access-control list of the file it replaces, on a file system
     * that has one (Windows); nothing when either view is absent.
     */
    static void copyAcl(@Nullable AclFileAttributeView from, @Nullable AclFileAttributeView to) throws IOException {
        if (from == null || to == null) {
            return;
        }
        to.setAcl(from.getAcl());
    }

    /**
     * Gives the replacement file the owner of the file it replaces, through whichever owner view the
     * file system has (POSIX or ACL); left as the server's when it may not be assigned.
     */
    static void copyOwner(@Nullable FileOwnerAttributeView from, @Nullable FileOwnerAttributeView to) {
        if (from == null || to == null) {
            return;
        }
        try {
            to.setOwner(from.getOwner());
        } catch (IOException notAllowed) {
            // Only a privileged process may give a file away; the server's own account stays.
        }
    }

    /**
     * Gives the replacement file the hidden, system and archive flags of the file it replaces, on a
     * file system that has them (Windows); a flag that cannot be set is left as it is. The read-only
     * flag needs no copy: a read-only kit file is never replaced.
     */
    static void copyDosFlags(@Nullable DosFileAttributeView from, @Nullable DosFileAttributeView to) {
        if (from == null || to == null) {
            return;
        }
        try {
            DosFileAttributes attributes = from.readAttributes();
            to.setHidden(attributes.isHidden());
            to.setSystem(attributes.isSystem());
            to.setArchive(attributes.isArchive());
        } catch (IOException notSet) {
            // Cosmetic flags; the file's content and access are already right.
        }
    }

    /**
     * Gives the replacement file the creation time of the file it replaces, where the file system
     * records one; left as it is when it cannot be set.
     */
    static void copyCreationTime(@Nullable BasicFileAttributeView from, @Nullable BasicFileAttributeView to) {
        if (from == null || to == null) {
            return;
        }
        try {
            to.setTimes(null, null, from.readAttributes().creationTime());
        } catch (IOException notSet) {
            // Informational only; the replacement keeps its own creation time.
        }
    }

    /**
     * Gives {@code temp} what an in-place write would have kept of the file it replaces: its
     * access-control list where the file system has one (Windows), its permission bits, where the
     * server may set them its owner and group, its hidden/system/archive flags (Windows) and its creation
     * time. Nothing when there is no such file. A list or permission bits that cannot be copied fail the
     * save (the file stays as it was) rather than leave a file with wider access; the rest is best effort.
     * Not kept, as with any replace-by-rename, because Java cannot copy them: hard links, extended
     * attributes and alternate data streams, a Windows file's primary group and audit list, and on
     * Linux the entries of an extended (setfacl) access-control list beyond the permission bits.
     */
    private static void copyFileIdentity(Path target, Path temp) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        copyAcl(Files.getFileAttributeView(target, AclFileAttributeView.class),
                Files.getFileAttributeView(temp, AclFileAttributeView.class));
        PosixFileAttributeView targetPosix = Files.getFileAttributeView(target, PosixFileAttributeView.class);
        if (targetPosix != null) {
            PosixFileAttributes attributes = targetPosix.readAttributes();
            Files.setPosixFilePermissions(temp, attributes.permissions());
            try {
                Files.getFileAttributeView(temp, PosixFileAttributeView.class).setGroup(attributes.group());
            } catch (IOException notAllowed) {
                // The server's own group stays; the permission bits above are what an in-place write kept.
            }
        }
        copyOwner(Files.getFileAttributeView(target, FileOwnerAttributeView.class),
                Files.getFileAttributeView(temp, FileOwnerAttributeView.class));
        copyDosFlags(Files.getFileAttributeView(target, DosFileAttributeView.class),
                Files.getFileAttributeView(temp, DosFileAttributeView.class));
        copyCreationTime(Files.getFileAttributeView(target, BasicFileAttributeView.class),
                Files.getFileAttributeView(temp, BasicFileAttributeView.class));
    }

    /**
     * The module-private folder holding the write journal of kit files, beside (not inside) the kits
     * folder, so the kit loader never reads a journal.
     */
    Path journalFolder() {
        return new File(plugin.getResourceFolderPath(), "kit-journal").toPath();
    }

    /**
     * A named point in a kit file write ({@code journal-written}, {@code target-written}). A seam, empty
     * here, so a test can capture the files on disk at that moment as a crash would leave them.
     */
    void checkpoint(String point) {
    }

    /**
     * Writes the whole of {@code content} at the channel's position. A seam, package-private so a test
     * can stop a write part-way.
     */
    void writeInPlace(FileChannel channel, byte[] content) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * Moves a fully written temporary file over a kit file in one step ({@link StandardCopyOption#ATOMIC_MOVE}).
     * A seam, package-private so a test can make the move fail, or report that the file system does
     * not support an atomic move.
     * <p>
     * 以原子方式把写好的临时文件移动到礼包文件；包级可见，供测试模拟移动失败。
     *
     * @param source the written temporary file / 已写好的临时文件
     * @param target the kit file / 礼包文件
     * @throws IOException when the move failed / 移动失败时抛出
     */
    void atomicMove(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Writes a kit's item contents, and the only place in this module that does - the kit editor's
     * Save button is its single caller.
     * <p>
     * The master switch is enforced here for the same reason it is enforced in {@link #claimKit}:
     * the editor GUI outlives the {@code /kits edit} command that opened it, so a check at that
     * command has already passed by the time Save is pressed and cannot stop a write while the
     * system is off. Guarding the gateway covers the editor and any writer added later.
     * <p>
     * 保存入口处执行总开关：编辑界面的生命周期长于打开它的命令，命令处的检查拦不住之后的保存。
     *
     * @param kitName the kit to write / 目标礼包名
     * @param items   its new contents / 新的物品内容
     * @return the outcome, never null / 结果，不为 null
     */
    @Override
    public SaveResult saveKitItems(String kitName, ItemStack[] items) {
        if (!config.isEnabled()) {
            return SaveResult.SYSTEM_DISABLED;
        }

        KitDefinition kit = getKit(kitName);
        if (kit == null) {
            return SaveResult.FAILED;
        }

        // Filter out null/air items
        ItemStack[] validItems = Arrays.stream(items)
                .filter(item -> item != null && item.getType() != Material.AIR)
                .toArray(ItemStack[]::new);

        String serialized = serializeItems(validItems);
        if (serialized == null) {
            return SaveResult.FAILED;
        }
        // Two files loading as one kit: writing one leaves the other to win or lose in directory
        // order, and writing both can stop half-way, so nothing is written until one file remains.
        if (!conflictingFiles(kit.getName()).isEmpty()) {
            return SaveResult.FILE_CONFLICT;
        }

        // The live kit keeps the new items only when the file took them: after a failed save the
        // editor reports the failure, so a claim must still hand out what the file holds.
        String previous = kit.getItems();
        kit.setItems(serialized);
        boolean saved = false;
        try {
            saved = saveKitToFile(kit.getName(), kit);
        } finally {
            if (!saved) {
                kit.setItems(previous);
            }
        }
        if (saved) {
            return SaveResult.SUCCESS;
        }
        // The writer also refuses a second file that appeared after the gateway's check.
        return conflictingFiles(kit.getName()).isEmpty() ? SaveResult.FAILED : SaveResult.FILE_CONFLICT;
    }

    @Override
    public List<String> conflictingFiles(String kitName) {
        List<File> files = kitFilesOf(kitName.toLowerCase().trim());
        if (files == null || files.size() < 2) {
            return Collections.emptyList();
        }
        return fileNames(files);
    }

    private static List<String> fileNames(List<File> files) {
        List<String> names = new ArrayList<>();
        for (File file : files) {
            names.add(file.getName());
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Claims a kit for a player. This method is the module's only gateway to a kit: {@code
     * deliverKit} is private with this as its single caller, and the item, command and claim-row
     * effects are reachable only from there.
     * <p>
     * The master switch ({@code config.yml: enabled}) is therefore enforced HERE and nowhere else
     * on the claim path. The two entry points that reach this method - {@code KitCommands} and the
     * browser's click handler - do not repeat the check; they render the {@link
     * ClaimResult#SYSTEM_DISABLED} this returns. That placement is deliberate and was chosen over
     * checking at each caller, because a guard repeated at N callers is only as good as whoever
     * remembers to add the N+1st: this module's own history has that enumeration coming up short
     * twice. Placed at the gateway, a caller added later cannot bypass the switch even by accident
     * (UltiKits/UltiKits#13).
     * <p>
     * 总开关只在这里执行一次。调用方不再各自检查，而是渲染本方法返回的 {@code SYSTEM_DISABLED}，
     * 这样将来新增的调用方无法绕过开关。
     *
     * @param player  the claiming player / 领取的玩家
     * @param kitName the kit's name / 礼包名
     * @return the outcome, never null / 领取结果，不为 null
     */
    @Override
    public ClaimResult claimKit(Player player, String kitName) {
        if (!config.isEnabled()) {
            return ClaimResult.SYSTEM_DISABLED;
        }

        KitDefinition kit = getKit(kitName);
        if (kit == null) {
            return ClaimResult.NOT_FOUND;
        }

        ClaimResult validationResult = validateClaim(player, kit);
        if (validationResult != null) {
            return validationResult;
        }

        ItemStack[] items = deserializeItems(kit.getItems());
        if (items == null || items.length == 0) {
            return ClaimResult.EMPTY_KIT;
        }

        if (!fitsInStorage(player, items)) {
            return ClaimResult.INVENTORY_FULL;
        }

        return deliverKit(player, kit, items);
    }

    /**
     * Validates player eligibility to claim a kit.
     * Returns null if all checks pass, or the failure result.
     */
    @Nullable
    ClaimResult validateClaim(Player player, KitDefinition kit) {
        ClaimResult prereq = checkPrerequisites(player, kit);
        if (prereq != null) {
            return prereq;
        }
        ClaimResult cooldown = checkCooldown(player, kit);
        if (cooldown != null) {
            return cooldown;
        }
        return kit.hasItems() ? null : ClaimResult.EMPTY_KIT;
    }

    @Nullable
    private ClaimResult checkPrerequisites(Player player, KitDefinition kit) {
        if (kit.hasPermission() && !player.hasPermission(kit.getPermission())) {
            return ClaimResult.NO_PERMISSION;
        }
        if (kit.hasLevelRequirement() && player.getLevel() < kit.getLevelRequired()) {
            return ClaimResult.INSUFFICIENT_LEVEL;
        }
        if (!kit.isFree() && !canAfford(player, kit.getPrice())) {
            return ClaimResult.INSUFFICIENT_FUNDS;
        }
        return null;
    }

    private boolean canAfford(Player player, double price) {
        return EconomyUtils.isAvailable() && EconomyUtils.has(player, price);
    }

    @Nullable
    private ClaimResult checkCooldown(Player player, KitDefinition kit) {
        KitClaimData claim = getClaimData(player.getUniqueId(), kit.getName());
        if (claim == null) {
            return null;
        }
        if (kit.isOneTime()) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        return getRemainingCooldown(player, kit) > 0 ? ClaimResult.ON_COOLDOWN : null;
    }

    /**
     * Whether the kit's stacks fit the player's storage slots, worked out the way {@code addItem} fills
     * them: each stack first tops up matching partial stacks ({@link ItemStack#isSimilar}) - those the
     * player holds and those an earlier stack of this kit started - and then takes empty slots, every
     * slot holding at most the stack's <b>own</b> maximum stack size ({@link ItemStack#getMaxStackSize()},
     * which reads a {@code max_stack_size} component), capped by the inventory's maximum when that is
     * lower.
     * <p>
     * One slot per stack was not the answer: a stack larger than its maximum - which another plugin, or
     * a component, can put into an inventory that {@code /kits create} then captures - is split across
     * several slots, so 128 cobblestone needs two; counting one let the claim pass, and {@code addItem}'s
     * leftovers were then discarded after the player had paid (UltiKits/UltiKits#24). Counting only
     * empty slots over-reserved instead, refusing a claim that fits into room left in a partial stack.
     * Anything this still gets wrong is dropped at the player's feet by {@link #giveOrDrop}, never
     * destroyed.
     * <p>
     * 按背包实际的放置方式判断能否放下：先补满相同物品的未满堆，再占用空格，每格上限为物品堆自身的最大堆叠数。
     */
    private boolean fitsInStorage(Player player, ItemStack[] items) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int inventoryMax = player.getInventory().getMaxStackSize();
        ItemStack[] slots = new ItemStack[contents.length];
        int[] amounts = new int[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() != Material.AIR) {
                slots[i] = stack;
                amounts[i] = stack.getAmount();
            }
        }
        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }
            int perSlot = Math.max(1, item.getMaxStackSize());
            if (inventoryMax > 0) {
                perSlot = Math.min(perSlot, inventoryMax);
            }
            int remaining = Math.max(1, item.getAmount());
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                if (slots[i] != null && amounts[i] < perSlot && slots[i].isSimilar(item)) {
                    int moved = Math.min(perSlot - amounts[i], remaining);
                    amounts[i] += moved;
                    remaining -= moved;
                }
            }
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                if (slots[i] == null) {
                    slots[i] = item;
                    amounts[i] = Math.min(perSlot, remaining);
                    remaining -= amounts[i];
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Charges for the kit, records the claim, then delivers it. The order is the load-bearing part.
     * <p>
     * The price is taken <b>first</b> and its result checked, so a refused payment leaves nothing
     * half-applied - no claim record, no items, no reward commands. Paying after delivery was
     * rejected: undoing a delivery means reclaiming items the player may already have moved,
     * equipped or traded, which is not a compensation anyone can trust (UltiKits/UltiKits#20).
     * <p>
     * The claim record is written <b>second</b>, before anything is handed over, and a record that
     * cannot be written refuses the claim: the price is refunded, nothing is given, no command runs,
     * and the player is told to try again. This is the maintainer's decision of 2026-09-24 for a
     * one-time claim whose record cannot be written - write the record first, refuse on a failed
     * write, and for a paid kit keep the charge-first order and refund (UltiKits/UltiKits#26). The
     * previous order handed the items over first, and because {@link #getClaimData} re-reads the
     * table on every claim, a write that failed made a one-time kit claimable again at once. Both
     * write failures are caught: the checked {@code IllegalAccessException} {@code update} declares
     * and the unchecked {@code DataAccessException} the relational backends throw on any SQL error.
     * <p>
     * Then the items: every stack lands in the inventory or is dropped at the player's feet, never
     * discarded - {@link #claimKit} refuses before charging unless the slots each stack really needs
     * are free ({@link #fitsInStorage}), and {@code addItem}'s leftovers are dropped
     * (UltiKits/UltiKits#24). The reward commands run last, after the record, because they are the
     * step most likely to throw ({@code player.performCommand} propagates a third-party executor's
     * {@code CommandException}).
     * <p>
     * What this order costs, as the decision accepted it: while the claim table cannot be written,
     * nobody can claim a kit; and if the refund fails too, the player has paid for nothing until an
     * operator refunds them by hand - the result says so to the player, never "nothing was charged",
     * and an ERROR names the player, the kit and the amount. A reward command that fails after the
     * record is not retried. On the JSON storage backend a write only reaches an in-memory cache that
     * a timer flushes to disk, so a disk failure there cannot be seen at claim time; that is the
     * framework's storage contract and is not changed here.
     * <p>
     * 顺序：先扣款并检查结果，再写入领取记录（写入失败则退款并拒绝领取），然后发放物品，最后执行奖励命令。
     *
     * @return {@link ClaimResult#PAYMENT_FAILED} when the price could not be withdrawn,
     *         {@link ClaimResult#NOT_RECORDED} or {@link ClaimResult#NOT_RECORDED_REFUND_FAILED} when
     *         the claim record could not be written, otherwise {@link ClaimResult#SUCCESS}
     */
    private ClaimResult deliverKit(Player player, KitDefinition kit, ItemStack[] items) {
        // No isAvailable() term here on purpose: it would short-circuit this whole condition to
        // false if the Vault provider were deregistered after checkPrerequisites ran, delivering
        // the paid kit free - the very outcome this guard exists to stop (UltiKits/UltiKits#20).
        // The framework's bridge already returns false when no provider is registered, so the
        // term bought nothing and could only turn a refusal into a giveaway.
        if (!kit.isFree() && !EconomyUtils.withdraw(player, kit.getPrice())) {
            // checkPrerequisites saw the player could afford this, so reaching here means the
            // balance moved in between, the economy rejected the transaction, or the provider went
            // away.
            warnRefusedWithdrawalOnce(player, kit);
            return ClaimResult.PAYMENT_FAILED;
        }
        if (!updateClaimData(player.getUniqueId(), kit.getName())) {
            return refuseUnrecordedClaim(player, kit);
        }
        giveOrDrop(player, items);
        executePlayerCommands(player, kit.getPlayerCommands());
        executeConsoleCommands(player, kit.getConsoleCommands());
        return ClaimResult.SUCCESS;
    }

    /**
     * Refuses a claim whose record could not be written, after the price - if any - was taken:
     * refunds it, and when the refund fails as well, logs an ERROR naming the player, the kit and
     * the amount so an operator can refund by hand, and returns a result whose reply does not claim
     * the money came back (UltiKits/UltiKits#26).
     * <p>
     * 领取记录写入失败时拒绝领取：退款；退款也失败时记录包含玩家、礼包和金额的错误日志。
     */
    private ClaimResult refuseUnrecordedClaim(Player player, KitDefinition kit) {
        if (kit.isFree() || refund(player, kit.getPrice())) {
            return ClaimResult.NOT_RECORDED;
        }
        logger.error(String.format(plugin.i18n("kits.log.refund_failed"), player.getName(), kit.getName(),
                kit.getPrice()));
        return ClaimResult.NOT_RECORDED_REFUND_FAILED;
    }

    /** Returns the price to the player; an economy that throws counts as a failed refund. */
    private boolean refund(Player player, double amount) {
        try {
            return EconomyUtils.deposit(player, amount);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Adds each item to the player's inventory and drops whatever {@code addItem} hands back at the
     * player's location - each leftover exactly once - telling the player when anything was dropped.
     * The module-wide precedent for items owed to a player: UltiMail's {@code ItemReturns#giveOrDrop}
     * and UltiTrade's {@code TradeService#giveOrDrop} (UltiKits/UltiKits#24).
     * <p>
     * 逐个发放物品；放不下的部分掉落在玩家脚下（每份只掉落一次），并提示玩家。
     */
    private void giveOrDrop(Player player, ItemStack[] items) {
        boolean dropped = false;
        for (ItemStack item : items) {
            if (item == null) {
                // The claim is already recorded, so nothing here may throw half-way through.
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            if (leftovers == null) {
                continue;
            }
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped = true;
            }
        }
        if (dropped) {
            player.sendMessage(ChatColor.YELLOW + plugin.i18n("kits.claim.leftovers_dropped"));
        }
    }

    /**
     * Writes one console warning per kit per server session for a refused withdrawal. See
     * {@link #refusalWarnedKits} for why it is throttled and how an operator re-arms it.
     */
    private void warnRefusedWithdrawalOnce(Player player, KitDefinition kit) {
        if (!refusalWarnedKits.add(kit.getName())) {
            return;
        }
        logger.warn(String.format(plugin.i18n("kits.log.payment_refused"), kit.getName(), player.getName(),
                kit.getPrice()));
    }

    @Override
    public long getRemainingCooldown(Player player, KitDefinition kit) {
        if (kit.isOneTime()) {
            KitClaimData claim = getClaimData(player.getUniqueId(), kit.getName());
            return claim != null ? -1 : 0;
        }

        KitClaimData claim = getClaimData(player.getUniqueId(), kit.getName());
        if (claim == null) {
            return 0;
        }

        long cooldownEnd = claim.getLastClaim() + (kit.getCooldown() * 1000);
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    @Override
    public String formatCooldown(long millis) {
        if (millis <= 0) {
            return plugin.i18n("kits.status.available");
        }

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(String.format(plugin.i18n("kits.cooldown.hours"), hours)).append(" ");
        }
        if (minutes > 0) {
            sb.append(String.format(plugin.i18n("kits.cooldown.minutes"), minutes)).append(" ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(String.format(plugin.i18n("kits.cooldown.seconds"), seconds));
        }

        return sb.toString().trim();
    }

    @Override
    public String serializeItems(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (IOException e) {
            logger.error(String.format(plugin.i18n("kits.log.serialize_failed"), e.getMessage()));
            return null;
        }
    }

    @Nullable
    @Override
    public ItemStack[] deserializeItems(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];

            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (IOException | ClassNotFoundException e) {
            logger.error(String.format(plugin.i18n("kits.log.deserialize_failed"), e.getMessage()));
            return null;
        }
    }

    // --- Internal methods ---

    @Nullable
    KitClaimData getClaimData(UUID playerUuid, String kitName) {
        List<KitClaimData> claims = claimOperator.query()
                .where("player_uuid").eq(playerUuid.toString())
                .list();

        return claims.stream()
                .filter(c -> c.getKitName().equalsIgnoreCase(kitName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Writes a claim: a new row on the first claim, the existing row's time and count after that.
     * <p>
     * Returns whether the write happened, and never throws for a storage failure: the relational
     * backends throw the unchecked {@link DataAccessException} on any SQL error - from {@code insert},
     * {@code update} or the read before them - and {@code update} also declares
     * {@code IllegalAccessException}. The caller refuses the claim on {@code false}
     * (UltiKits/UltiKits#26); before, the {@code insert} was unguarded and only the checked exception
     * was caught, so a database error escaped after the kit had been handed over.
     * <p>
     * 写入领取记录；返回是否写入成功，存储失败时不抛出异常。
     *
     * @return {@code true} when the row was written / 写入成功时为 {@code true}
     */
    boolean updateClaimData(UUID playerUuid, String kitName) {
        try {
            KitClaimData existing = getClaimData(playerUuid, kitName);

            if (existing != null) {
                existing.setLastClaim(System.currentTimeMillis());
                existing.setClaimCount(existing.getClaimCount() + 1);
                claimOperator.update(existing);
            } else {
                KitClaimData claim = KitClaimData.builder()
                        .uuid(UUID.randomUUID().toString())
                        .playerUuid(playerUuid.toString())
                        .kitName(kitName)
                        .lastClaim(System.currentTimeMillis())
                        .claimCount(1)
                        .build();
                claimOperator.insert(claim);
            }
            return true;
        } catch (IllegalAccessException | DataAccessException e) {
            logger.error(String.format(plugin.i18n("kits.log.claim_update_failed"), e.getMessage()));
            return false;
        }
    }

    @Nullable
    KitDefinition parseKitFile(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            KitDefinition kit = new KitDefinition();
            String displayName = config.getString("displayName");
            if (displayName == null) {
                kit.useCatalogueDisplayName("&7" + plugin.i18n("kits.kit.default_display_name"));
            } else {
                kit.setDisplayName(displayName);
            }
            kit.setDescription(config.getStringList("description"));
            kit.setPrice(config.getDouble("price", 0));
            kit.setLevelRequired(config.getInt("levelRequired", 0));
            kit.setPermission(config.getString("permission", ""));
            kit.setReBuyable(config.getBoolean("reBuyable", false));
            kit.setCooldown(config.getLong("cooldown", 0));
            kit.setPlayerCommands(config.getStringList("playerCommands"));
            kit.setConsoleCommands(config.getStringList("consoleCommands"));
            kit.setItems(config.getString("items", ""));

            // Validate icon material
            String iconStr = config.getString("icon", "CHEST");
            try {
                Material.valueOf(iconStr.toUpperCase());
                kit.setIcon(iconStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn(String.format(plugin.i18n("kits.log.invalid_icon"), file.getName(), iconStr));
                kit.setIcon("CHEST");
            }

            return kit;
        } catch (Exception e) {
            logger.warn(String.format(plugin.i18n("kits.log.load_failed"), file.getName(), e.getMessage()));
            return null;
        }
    }

    boolean saveKitToFile(String name, KitDefinition kit) {
        try {
            // Write the file the kit loads from, so a save lands where the next reload reads it; a new
            // kit gets "<name>.yml". A folder that cannot be listed gives no way to know which file
            // that is, and a kit that several files define has no single one, so both fail rather than
            // writing a file beside the real one. The write itself is all-or-nothing (writeAtomically).
            List<File> targets = kitFilesOf(name);
            if (targets == null) {
                logger.error(String.format(plugin.i18n("kits.log.kits_folder_unreadable_save"),
                        kitsFolder().getAbsolutePath(), name));
                return false;
            }
            if (targets.size() > 1) {
                // Never write one of several files a kit loads from (see conflictingFiles).
                return false;
            }
            if (targets.isEmpty()) {
                targets = Collections.singletonList(new File(plugin.getResourceFolderPath(), "kits/" + name + ".yml"));
            }
            YamlConfiguration config = new YamlConfiguration();

            // A fallback name is left out, so it keeps following the language (null writes nothing).
            config.set("displayName", kit.isDisplayNameFromCatalogue() ? null : kit.getDisplayName());
            config.set("description", kit.getDescription());
            config.set("icon", kit.getIcon());
            config.set("price", kit.getPrice());
            config.set("levelRequired", kit.getLevelRequired());
            config.set("permission", kit.getPermission());
            config.set("reBuyable", kit.isReBuyable());
            config.set("cooldown", kit.getCooldown());
            config.set("playerCommands", kit.getPlayerCommands());
            config.set("consoleCommands", kit.getConsoleCommands());
            config.set("items", kit.getItems());

            writeAtomically(targets.get(0), config.saveToString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | RuntimeException e) {
            // A refused write is a failed save whatever its exception type; the callers answer false.
            logger.error(String.format(plugin.i18n("kits.log.save_file_failed"), name, e.getMessage()));
            return false;
        }
    }

    private void copyExampleKit(File folder) {
        try (InputStream is = plugin.getClass().getClassLoader().getResourceAsStream("kits/starter.yml")) {
            File exampleFile = new File(folder, "starter.yml");
            if (is != null && !exampleFile.exists()) {
                // Written like any kit file, so a copy that fails part-way leaves no cut-off
                // starter.yml, which would stop every later start from copying it again.
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                for (int read = is.read(chunk); read != -1; read = is.read(chunk)) {
                    bytes.write(chunk, 0, read);
                }
                writeAtomically(exampleFile, bytes.toByteArray());
            }
        } catch (IOException | RuntimeException e) {
            logger.warn(String.format(plugin.i18n("kits.log.example_copy_failed"), e.getMessage()));
        }
    }

    private void executePlayerCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String cmd : commands) {
            String processed = cmd.replace("{player}", player.getName());
            player.performCommand(processed);
        }
    }

    private void executeConsoleCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        org.bukkit.plugin.Plugin ultiToolsPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
        if (ultiToolsPlugin == null) {
            return;
        }
        for (String cmd : commands) {
            String processed = cmd.replace("{player}", player.getName());
            String finalCmd = processed;
            Bukkit.getScheduler().runTask(ultiToolsPlugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
        }
    }
}
