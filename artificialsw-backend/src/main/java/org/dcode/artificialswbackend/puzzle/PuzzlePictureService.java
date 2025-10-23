package org.dcode.artificialswbackend.puzzle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.dcode.artificialswbackend.archive.entity.IslandArchives;
import org.dcode.artificialswbackend.archive.entity.Tree;
import org.dcode.artificialswbackend.archive.repository.IslandArchivesRepository;
import org.dcode.artificialswbackend.archive.repository.TreeRepository;
import org.dcode.artificialswbackend.puzzle.dto.*;
import org.dcode.artificialswbackend.puzzle.entity.*;
import org.dcode.artificialswbackend.puzzle.repository.*;
import org.dcode.artificialswbackend.puzzle.util.DateSegmentInfo;
import org.dcode.artificialswbackend.puzzle.util.DateSegmentUtil;
import org.dcode.artificialswbackend.signup.repository.SignUpRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PuzzlePictureService {
    private final PuzzleRepository puzzleRepository;
    private final PuzzleCategoryRepository puzzleCategoryRepository;
    private final PuzzlePiecesRepository puzzlePiecesRepository;
    private final FruitCatalogRepository fruitCatalogRepository;
    private final SignUpRepository signUpRepository;
    private final IslandArchivesRepository islandArchivesRepository;
    private final PuzzleArchiveRepository puzzleArchiveRepository;
    private final FruitsRepository fruitsRepository;
    private final TreeRepository treeRepository;
    private final FamilyFruitStatusRepository familyFruitStatusRepository;
    private final ObjectMapper objectMapper;

    @Value("${puzzle.image.url.base}")
    private String imageBaseUrl;

    public PuzzlePictureService(PuzzleRepository puzzleRepository, PuzzleCategoryRepository puzzleCategoryRepository, PuzzlePiecesRepository puzzlePiecesRepository, FruitCatalogRepository fruitCatalogRepository, SignUpRepository signUpRepository, IslandArchivesRepository islandArchivesRepository, PuzzleArchiveRepository puzzleArchiveRepository, FruitsRepository fruitsRepository, TreeRepository treeRepository, FamilyFruitStatusRepository familyFruitStatusRepository, ObjectMapper objectMapper) {
        this.puzzleRepository = puzzleRepository;
        this.puzzleCategoryRepository = puzzleCategoryRepository;
        this.puzzlePiecesRepository = puzzlePiecesRepository;
        this.fruitCatalogRepository = fruitCatalogRepository;
        this.signUpRepository = signUpRepository;
        this.islandArchivesRepository = islandArchivesRepository;
        this.puzzleArchiveRepository = puzzleArchiveRepository;
        this.fruitsRepository = fruitsRepository;
        this.treeRepository = treeRepository;
        this.familyFruitStatusRepository = familyFruitStatusRepository;
        this.objectMapper = objectMapper;
    }
    @Transactional
    public List<String> savePictures(List<PictureData> pictureDataList, Long userId, Long familyId) {
        List<String> imageUrls = new ArrayList<>();
        for (PictureData data : pictureDataList) {

            // 1. 이미지 저장
            String uploadDir = "/home/ubuntu/app/images/";
            String fileName = System.currentTimeMillis() + ".png";
            byte[] decodedBytes = Base64.getDecoder().decode(data.getImageBase64());
            try {
                Files.write(Paths.get(uploadDir + fileName), decodedBytes);
            } catch (IOException e) {
                throw new RuntimeException("이미지 저장 실패");
            }
            String imageUrl = imageBaseUrl  + fileName;

            // 2. 카테고리 엔티티 조회 (DB에 반드시 존재한다고 가정)
            PuzzleCategory categoryEntity = puzzleCategoryRepository.findByCategory(data.getCategory());
            if (categoryEntity == null) {
                throw new RuntimeException("카테고리가 DB에 존재하지 않습니다: " + data.getCategory());
            }

            // 3. puzzle 테이블 저장, 카테고리 FK로 연결
            Puzzle puzzle = new Puzzle();
            puzzle.setImagePath(imageUrl);
            puzzle.setFamiliesId(familyId);
            puzzle.setMessage(data.getComment());
            puzzle.setCategory(categoryEntity); // 카테고리 엔티티 연결
            puzzleRepository.save(puzzle);

            imageUrls.add(imageUrl);
        }
        return imageUrls;
    }
    @Transactional
    public PuzzleCreateResponse createPuzzle(int size, String userId, Long familyId) {
        // 1. 랜덤 퍼즐 선택
        Puzzle puzzle = puzzleRepository.findRandomBePuzzleZeroByFamilyId(familyId);
        if (puzzle == null) throw new RuntimeException("랜덤 퍼즐이 없습니다.");

        // 2. 퍼즐 정보 갱신
        puzzle.setSize(size);
        puzzle.setContributors("[\"" + userId + "\"]");
        puzzle.setBePuzzle(1); // 반드시 여기서 bePuzzle 값 1로 변경!
        // 진행 상태로 변경
        puzzle.setIs_playing_puzzle(true);

        puzzleRepository.save(puzzle);

        return new PuzzleCreateResponse(
                puzzle.getPuzzleId(),
                puzzle.getImagePath(),
                puzzle.getCategory().getCategory(),
                "퍼즐이 생성되었습니다."
        );
    }


    public String saveCaptureImage(String base64Image) {
        String uploadDir = "/home/ubuntu/app/images/capture/";
        String fileName = System.currentTimeMillis() + ".png";

        byte[] decodedBytes = Base64.getDecoder().decode(base64Image);
        try {
            Files.write(Paths.get(uploadDir + fileName), decodedBytes);
        } catch (IOException e) {
            throw new RuntimeException("캡쳐 이미지 저장 실패", e);
        }

        return imageBaseUrl + "capture/" + fileName;
    }

    public Puzzle getPuzzleById(Integer puzzleId) {
        Optional<Puzzle> optionalPuzzle = puzzleRepository.findById(puzzleId);
        if (optionalPuzzle.isEmpty()) {
            throw new RuntimeException("Puzzle not found with id: " + puzzleId);
        }
        return optionalPuzzle.get();
    }

    public void updatePuzzleStatus(Puzzle puzzle, String savedCaptureImagePath, boolean completed, boolean isPlayingPuzzle) {
        puzzle.setCapture_image_path(savedCaptureImagePath);
        puzzle.setCompleted(completed);
        puzzle.setIs_playing_puzzle(isPlayingPuzzle);
    }


    @Transactional
    public void updatePuzzlePieces(Puzzle puzzle, Map<String, SavePuzzleProgressRequest.Coordinate> piecesMap) {
        try {
            // piecesMap을 "pieces" 키로 감싸 JSON 문자열로 변환
            Map<String, Object> positionMap = new HashMap<>();
            positionMap.put("pieces", piecesMap);
            String positionJson = objectMapper.writeValueAsString(positionMap);

            // 퍼즐에 해당하는 PuzzlePieces 엔티티 조회 또는 생성
            PuzzlePieces puzzlePieces = puzzlePiecesRepository.findByPuzzleId(puzzle.getPuzzleId())
                    .orElseGet(() -> {
                        PuzzlePieces newPiece = new PuzzlePieces();
                        newPiece.setPuzzleId(puzzle.getPuzzleId());
                        return newPiece;
                    });

            // position 필드에 JSON 문자열 저장
            puzzlePieces.setPosition(positionJson);
            puzzlePiecesRepository.save(puzzlePieces);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("퍼즐 조각 위치 JSON 변환 실패", e);
        }
    }

    public void updateCompletedPiecesId(Puzzle puzzle, List<Integer> completedPiecesId) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(completedPiecesId);
            puzzle.setCompleted_pieces_id(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("completedPiecesId JSON 변환 실패", e);
        }
    }

    // contributor 반영
    public void updateContributor(Puzzle puzzle, Long userId) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            // 이미 contributors가 있으면 merge, 없으면 새로 추가(최소 한 명 저장)
            String contributorsJson = puzzle.getContributors();
            List<Long> contributorsList;
            if (contributorsJson != null && !contributorsJson.isEmpty()) {
                contributorsList = mapper.readValue(contributorsJson, mapper.getTypeFactory().constructCollectionType(List.class, Long.class));
                if (!contributorsList.contains(userId)) {
                    contributorsList.add(userId);
                }
            } else {
                contributorsList = List.of(userId);
            }
            puzzle.setContributors(mapper.writeValueAsString(contributorsList));
        } catch (Exception e) {
            throw new RuntimeException("Contributors JSON 처리 실패", e);
        }
    }

    @Transactional
    public void savePuzzleProgress(Long userId, Integer puzzleId, Long familyId, SavePuzzleProgressRequest request) {
        Puzzle puzzle = getPuzzleById(puzzleId);
        // familyId 검증 추가
        if (!puzzle.getFamiliesId().equals(familyId)) {
            throw new RuntimeException("퍼즐이 해당 가족에 속하지 않습니다.");
        }
        puzzle.setIs_playing_puzzle(false); //미진행 상태로 변경
        String savedCaptureImagePath = saveCaptureImage(request.getCaptureImagePath());
        updatePuzzleStatus(puzzle, savedCaptureImagePath, request.isCompleted(), request.isPlayingPuzzle());
        updatePuzzlePieces(puzzle, request.getPieces());
        updateCompletedPiecesId(puzzle, request.getCompletedPiecesId());
        updateContributor(puzzle, userId);
        puzzle.setLastSavedTime(LocalDateTime.now());
        puzzleRepository.save(puzzle);
    }


    @Transactional
    public PuzzleProgressResponse getPuzzleProgress(Integer puzzleId) {
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new RuntimeException("Puzzle not found with id: " + puzzleId));

        if (puzzle.getIs_playing_puzzle()) {
            throw new IllegalStateException("다른 사람이 퍼즐을 풀고 있습니다");
        }

        // 진행 상태로 변경
        puzzle.setIs_playing_puzzle(true);
        puzzleRepository.save(puzzle);

        Optional<PuzzlePieces> puzzlePiecesOpt = puzzlePiecesRepository.findByPuzzleId(puzzleId);

        Map<String, PuzzleProgressResponse.PiecePosition> pieces = new HashMap<>();

        if (puzzlePiecesOpt.isPresent()) {
            PuzzlePieces puzzlePieces = puzzlePiecesOpt.get();
            String positionJson = puzzlePieces.getPosition();

            try {
                JsonNode rootNode = objectMapper.readTree(positionJson);
                JsonNode piecesNode = rootNode.get("pieces");
                if (piecesNode != null) {
                    Iterator<String> fieldNames = piecesNode.fieldNames();
                    while (fieldNames.hasNext()) {
                        String key = fieldNames.next();
                        JsonNode posNode = piecesNode.get(key);
                        double x = posNode.get("x").asDouble();
                        double y = posNode.get("y").asDouble();
                        pieces.put(key, new PuzzleProgressResponse.PiecePosition(x, y));
                    }
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error parsing pieces JSON", e);
            }
        }

        return new PuzzleProgressResponse(
                puzzle.getImagePath(),
                puzzle.getSize(),
                pieces
        );
    }



    @Transactional
    public PuzzleCompleteResponse completePuzzle(
            Integer puzzleId,
            Long solverId,
            Long familyId,
            int month
    ) {
        // 1. 날짜 및 포지션 계산
        DateSegmentInfo dateInfo = DateSegmentUtil.getDateSegmentInfo();
        int position = dateInfo.getPosition();
        int period = dateInfo.getPeriod();
        int archiveYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        int archiveMonth = LocalDate.now(ZoneId.of("Asia/Seoul")).getMonthValue();

        // 2. 퍼즐 조회/완료 처리
        Puzzle puzzle = getPuzzleById(puzzleId);
        puzzle.setIs_playing_puzzle(false);
        puzzle.setCompleted(true);
        puzzle.setSolverId(solverId);
        puzzle.setCompletedTime(LocalDateTime.now());
        puzzleRepository.save(puzzle);

        // 3. 계절 계산
        String season = getSeasonByMonth(month);

        // 4. 계절별 과일 랜덤 선정 (항상 하나 생성함)
        List<FruitCatalog> seasonalFruits = fruitCatalogRepository.findBySeason(season);
        FruitCatalog fruit = seasonalFruits.get(new Random().nextInt(seasonalFruits.size()));
        String fruitName = fruit.getFruitName();
        String fruitMessage = getFruitMessage(fruit.getId(), fruitName);

        // 5. 도감 해금 로직 (가족별로만 체크)
        Long fruitId = fruit.getId().longValue();
        Optional<FamilyFruitStatus> statusOpt = familyFruitStatusRepository.findByFamilyIdAndFruitId(familyId, fruitId);

        if (statusOpt.isEmpty()) {
            FamilyFruitStatus newStatus = new FamilyFruitStatus();
            newStatus.setFamilyId(familyId);
            newStatus.setFruitId(fruitId);
            newStatus.setUnlocked(true);
            newStatus.setUnlockedAt(LocalDateTime.now());
            familyFruitStatusRepository.save(newStatus);
        } else if (!statusOpt.get().isUnlocked()) {
            FamilyFruitStatus status = statusOpt.get();
            status.setUnlocked(true);
            status.setUnlockedAt(LocalDateTime.now());
            familyFruitStatusRepository.save(status);
        }
        // 이미 해금 상태면 아무것도 안함

        // 6. contributors 닉네임 리스트 생성
        List<Long> contributorIds = parseContributors(puzzle.getContributors());
        List<String> contributorNicknames = new ArrayList<>();
        for (Long id : contributorIds) {
            signUpRepository.findByIdAndFamilyId(id, familyId)
                    .ifPresent(user -> contributorNicknames.add(user.getNickname()));
        }
        signUpRepository.findByIdAndFamilyId(solverId, familyId)
                .ifPresent(user -> {
                    if (!contributorNicknames.contains(user.getNickname())) {
                        contributorNicknames.add(user.getNickname());
                    }
                });

        // 7. 퍼즐 점수 증가 (최대 8점)
        IslandArchives island = islandArchivesRepository.findByFamilyIdAndYearAndMonthAndPeriod(
                        familyId, archiveYear, archiveMonth, period)
                .orElseThrow(() -> new RuntimeException("아카이브 레코드가 없습니다"));
        int currentScore = island.getPuzzleScore() != null ? island.getPuzzleScore() : 0;
        if (currentScore < 8) {
            island.setPuzzleScore(currentScore + 1);
            islandArchivesRepository.save(island);
        }

        // 8. 트리 찾아 과일 기록
        Tree tree = treeRepository.findByArchiveIdAndFamilyIdAndPositionAndTreeCategory(
                        island.getId(), familyId, position, Tree.TreeCategory.fruit)
                .orElseThrow(() -> new RuntimeException("조건에 맞는 트리가 없습니다"));

        Fruits fruitEntity = new Fruits();
        fruitEntity.setTreeId(tree.getId());
        fruitEntity.setPuzzleId(puzzle.getPuzzleId());
        fruitEntity.setMessage(fruitMessage);
        fruitEntity.setFruitName(fruitName);
        fruitEntity.setCategory(puzzle.getCategory().getCategory());
        fruitEntity.setContributors(puzzle.getContributors());
        fruitEntity.setCreatedAt(puzzle.getCompletedTime());
        fruitsRepository.save(fruitEntity);

        // 9. 응답 DTO 생성 및 반환
        return new PuzzleCompleteResponse(
                puzzle.getPuzzleId().longValue(),
                puzzle.getMessage(),
                fruitName,
                fruitMessage,
                contributorNicknames
        );
    }

    // 계절 변환 유틸
    private String getSeasonByMonth(int month) {
        if (month >= 3 && month <= 5) return "spring";
        if (month >= 6 && month <= 9) return "summer";
        if (month >= 10 && month <= 11) return "fall";
        return "winter";
    }

    // 메시지 유틸
    private String getFruitMessage(Integer fruitId, String fruitName) {
        return switch (fruitId) {
            case 1 -> "사랑스러운 봄의 체리 획득!";
            case 2 -> "사랑스러운 봄의 딸기 획득!";
            case 3 -> "사랑스러운 봄의 키위 획득!";
            case 4 -> "사랑스러운 봄의 산딸기 획득!";
            case 5 -> "향긋한 여름의 망고 획득!";
            case 6 -> "향긋한 여름의 복숭아 획득!";
            case 7 -> "향긋한 여름의 자두 획득!";
            case 8 -> "향긋한 여름의 블루베리 획득!";
            case 9 -> "포근한 가을의 대추 획득!";
            case 10 -> "포근한 가을의 포도 획득!";
            case 11 -> "포근한 가을의 배 획득!";
            case 12 -> "포근한 가을의 감 획득!";
            case 13 -> "탐스러운 겨울 유자 획득!";
            case 14 -> "탐스러운 겨울 석류 획득!";
            case 15 -> "탐스러운 겨울 사과 획득!";
            case 16 -> "탐스러운 겨울 귤 획득!";
            default -> fruitName + " 획득!";
        };
    }

    // contributions JSON → 리스트 변환 유틸
    private List<Long> parseContributors(String contributorsJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(
                    contributorsJson,
                    mapper.getTypeFactory().constructCollectionType(List.class, Long.class)
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Transactional
    public List<PuzzleInProgressResponse> getInProgressPuzzles(Long familyId) {
        List<Puzzle> puzzles = puzzleRepository.findByFamiliesIdAndCompletedAndBePuzzle(familyId, false, 1);
        List<PuzzleInProgressResponse> responses = new ArrayList<>();

        for (Puzzle puzzle : puzzles) {
            List<String> userIdList = new ArrayList<>();
            try {
                if (puzzle.getContributors() != null) {
                    userIdList = objectMapper.readValue(
                            puzzle.getContributors(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                    );
                }
            } catch (Exception e) {
                // parsing 실패 시 빈 리스트
            }

            // userId -> familyType 변환
            List<String> familyTypeList = new ArrayList<>();
            for (String userIdStr : userIdList) {
                try {
                    Long userId = Long.parseLong(userIdStr);
                    String familyType = signUpRepository.findFamilyTypeById(userId);
                    if (familyType != null && !familyTypeList.contains(familyType)) {
                        familyTypeList.add(familyType);
                    }
                } catch (Exception e) {
                    // 로그 혹은 예외 처리
                }
            }

            int completedPiecesCount = 0;
            try {
                if (puzzle.getCompleted_pieces_id() != null) {
                    List<Integer> completedPieces = objectMapper.readValue(
                            puzzle.getCompleted_pieces_id(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Integer.class)
                    );
                    completedPiecesCount = completedPieces.size();
                }
            } catch (Exception e) {
                completedPiecesCount = 0;
            }

            responses.add(new PuzzleInProgressResponse(
                    puzzle.getPuzzleId(),
                    puzzle.getCapture_image_path(),
                    familyTypeList,  // contributorsList 대신 familyTypeList 사용
                    puzzle.getCategory().getCategory(),
                    completedPiecesCount,
                    puzzle.getSize() != null ? puzzle.getSize() : 0
            ));
        }
        return responses;
    }

    @Transactional
    public void deletePuzzle(Integer puzzleId) {
        if (!puzzleRepository.existsById(puzzleId)) {
            throw new IllegalArgumentException("존재하지 않는 퍼즐입니다: " + puzzleId);
        }
        puzzleRepository.deleteById(puzzleId);
    }


    @Transactional
    public List<PuzzleCompletedResponse> getCompletedPuzzles(Long familyId) {
        List<Puzzle> puzzles = puzzleRepository.findByFamiliesIdAndCompleted(familyId, true);
        List<PuzzleCompletedResponse> responses = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        for (Puzzle puzzle : puzzles) {
            List<String> userIdList = new ArrayList<>();
            try {
                if (puzzle.getContributors() != null) {
                    userIdList = mapper.readValue(
                            puzzle.getContributors(),
                            mapper.getTypeFactory().constructCollectionType(List.class, String.class)
                    );
                }
            } catch (Exception e) {
                // parsing 실패 시 빈 리스트
            }

            // solverId를 추가
            if (puzzle.getSolverId() != null) {
                String solverIdStr = puzzle.getSolverId().toString();
                if (!userIdList.contains(solverIdStr)) {
                    userIdList.add(solverIdStr);
                }
            }

            // userId 리스트를 familyType 리스트로 변환 (중복 방지)
            List<String> familyTypeList = new ArrayList<>();
            for (String userIdStr : userIdList) {
                try {
                    Long userId = Long.parseLong(userIdStr);
                    String familyType = signUpRepository.findFamilyTypeById(userId);
                    if (familyType != null && !familyTypeList.contains(familyType)) {
                        familyTypeList.add(familyType);
                    }
                } catch (Exception e) {
                    // 예외 무시 또는 로깅
                }
            }

            // contributors 필드에 familyType 리스트를 JSON 문자열로 저장
            try {
                puzzle.setContributors(mapper.writeValueAsString(familyTypeList));
                puzzleRepository.save(puzzle);
            } catch (Exception e) {
                // 예외 무시 또는 로깅
            }

            responses.add(new PuzzleCompletedResponse(
                    puzzle.getPuzzleId(),
                    puzzle.getImagePath(),
                    puzzle.getCategory().getCategory(),
                    familyTypeList,
                    puzzle.getMessage()
            ));
        }
        return responses;
    }


    @Transactional
    public Map<String, Object> retryCompletedPuzzle(Integer puzzleId, Long familyId) {
        // 1. 퍼즐 찾기 (puzzleId, familyId)
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new IllegalArgumentException("퍼즐을 찾을 수 없습니다."));
        if (!puzzle.getFamiliesId().equals(familyId)) {
            throw new IllegalArgumentException("가족 정보가 일치하지 않습니다.");
        }

        // 2. 반드시 완성된 퍼즐인지 확인
        if (!puzzle.isCompleted()) {
            throw new IllegalStateException("완성된 퍼즐만 선택 가능합니다.");
        }

        // 3. 정보 반환
        return Map.of(
                "message", puzzle.getMessage() != null ? puzzle.getMessage() : "",
                "imageUrl", puzzle.getImagePath() != null ? puzzle.getImagePath() : "",
                "size", puzzle.getSize() != null ? puzzle.getSize() : 0
        );
    }

    @Transactional
    public void archivePuzzle(Integer puzzleId, Long familyId) {
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new IllegalArgumentException("퍼즐을 찾을 수 없습니다."));
        if (!puzzle.getFamiliesId().equals(familyId)) {
            throw new IllegalArgumentException("가족 정보가 일치하지 않습니다.");
        }
        // 반드시 완성된 퍼즐인지 확인
        if (!puzzle.isCompleted()) {
            throw new IllegalStateException("완성된 퍼즐만 아카이브할 수 있습니다.");
        }

        PuzzleArchive archive = new PuzzleArchive();
        archive.setImagePath(puzzle.getImagePath());
        archive.setCategory(puzzle.getCategory().getCategory());
        archive.setContributors(puzzle.getContributors());
        archive.setFamiliesId(familyId);
        archive.setArchivedAt(LocalDateTime.now());
        archive.setMessage(puzzle.getMessage());
        archive.setSize(puzzle.getSize());
        puzzleArchiveRepository.save(archive);

        puzzleRepository.delete(puzzle); // 퍼즐 삭제
    }

    @Transactional
    public List<PuzzleArchiveResponse> getArchivedPuzzles(Long familyId, Integer year) {
        List<PuzzleArchive> archives;
        if (year == null) {
            archives = puzzleArchiveRepository.findByFamiliesId(familyId);
        } else {
            LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0);
            LocalDateTime end = start.plusYears(1);
            archives = puzzleArchiveRepository.findByFamiliesIdAndArchivedAtBetween(familyId, start, end);
        }

        List<PuzzleArchiveResponse> responses = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        for (PuzzleArchive archive : archives) {
            List<String> contributorsList = new ArrayList<>();
            try {
                if (archive.getContributors() != null) {
                    contributorsList = mapper.readValue(
                            archive.getContributors(),
                            mapper.getTypeFactory().constructCollectionType(List.class, String.class)
                    );
                }
            } catch (Exception e) {
                // 파싱 실패시 빈 리스트 유지
            }
            responses.add(new PuzzleArchiveResponse(
                    archive.getId(),
                    archive.getImagePath(),
                    archive.getCategory(),
                    contributorsList,
                    archive.getArchivedAt()
            ));
        }
        return responses;
    }

    @Transactional
    public void deleteArchivedPuzzle(Long familyId, Long puzzleArchiveId) {
        PuzzleArchive archive = puzzleArchiveRepository.findById(puzzleArchiveId)
                .orElseThrow(() -> new IllegalArgumentException("아카이브 퍼즐을 찾을 수 없습니다."));
        if (!archive.getFamiliesId().equals(familyId)) {
            throw new IllegalArgumentException("가족 정보가 일치하지 않습니다.");
        }
        puzzleArchiveRepository.deleteById(puzzleArchiveId);
    }

    // 홈 화면 기능
    public List<String> getActiveCategorySet() {
        DateSegmentInfo dateInfo = DateSegmentUtil.getDateSegmentInfo();
        int startId = dateInfo.getSetIndex() * 3 + 1;
        int endId = Math.min(startId + 2, 100);

        List<PuzzleCategory> categories = puzzleCategoryRepository.findByIdBetween((long) startId, (long) endId);
        return categories.stream()
                .map(PuzzleCategory::getCategory)
                .collect(Collectors.toList());
    }


    public List<InProgressPuzzleDto> findInProgressForHome(Long familyId) {
        List<Puzzle> puzzles = puzzleRepository.findByFamiliesIdAndCompletedAndBePuzzle(familyId, false, 1);
        List<InProgressPuzzleDto> result = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        for (Puzzle puzzle : puzzles) {
            List<Integer> completedPiecesId = new ArrayList<>();
            try {
                if (puzzle.getCompleted_pieces_id() != null) {
                    completedPiecesId = mapper.readValue(
                            puzzle.getCompleted_pieces_id(),
                            mapper.getTypeFactory().constructCollectionType(List.class, Integer.class)
                    );
                }
            } catch (Exception e) {
                // 파싱 실패 시 빈 리스트
            }
            result.add(new InProgressPuzzleDto(
                    puzzle.getPuzzleId(),
                    puzzle.getCapture_image_path(),
                    completedPiecesId,
                    puzzle.getSize(),
                    puzzle.getLastSavedTime()
            ));
        }
        return result;
    }

    public List<CompletedPuzzleDto> findCompletedThisCycle(Long familyId) {
        // 1. 현재 활성화된 카테고리 세트 구하기
        List<String> activeCategoryNames = getActiveCategorySet();
        List<PuzzleCategory> categories = puzzleCategoryRepository.findByCategoryIn(activeCategoryNames);
        List<Long> categoryIds = categories.stream()
                .map(PuzzleCategory::getId)
                .collect(Collectors.toList());

        // 2. 완료 퍼즐 조회 (카테고리 세트 & familyId & completed = true)
        List<Puzzle> puzzles = puzzleRepository.findByFamiliesIdAndCompletedAndCategoryIdIn(familyId, true, categoryIds);

        // 3. DTO 변환
        List<CompletedPuzzleDto> result = new ArrayList<>();
        for (Puzzle puzzle : puzzles) {
            result.add(new CompletedPuzzleDto(
                    puzzle.getPuzzleId(),
                    puzzle.getImagePath(),
                    puzzle.getCategory().getCategory(),
                    puzzle.getSize(),
                    puzzle.getCompletedTime()
            ));
        }
        return result;
    }

    public boolean isFull(Long familyId, Integer userId) {
        List<String> activeCategoryNames = getActiveCategorySet();
        List<PuzzleCategory> categories = puzzleCategoryRepository.findByCategoryIn(activeCategoryNames);
        List<Long> categoryIds = categories.stream()
                .map(PuzzleCategory::getId)
                .collect(Collectors.toList());
        int count = puzzleRepository.countByFamiliesIdAndUploaderIdAndCategoryIdIn(familyId, userId, categoryIds);
        return count >= 3;
    }

    public boolean isEmpty(Long familyId) {
        int count = puzzleRepository.countByFamiliesIdAndBePuzzle(familyId,0);
        return count == 0;
    }

    public PuzzleHomeResponse getPuzzleHome(Long familyId, Integer userId) {
        List<String> category = getActiveCategorySet();
        List<InProgressPuzzleDto> inProgress = findInProgressForHome(familyId);
        List<CompletedPuzzleDto> completedThisWeek = findCompletedThisCycle(familyId);
        boolean isFull = isFull(familyId, userId);
        boolean isEmpty = isEmpty(familyId);
        return new PuzzleHomeResponse(category, inProgress, completedThisWeek, isFull, isEmpty);
    }
}