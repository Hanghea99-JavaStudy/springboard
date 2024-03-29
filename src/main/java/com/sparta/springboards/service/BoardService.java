package com.sparta.springboards.service;

import com.sparta.springboards.dto.*;
import com.sparta.springboards.entity.*;
import com.sparta.springboards.exception.CustomException;
import com.sparta.springboards.repository.BoardLikeRepository;
import com.sparta.springboards.repository.BoardRepository;
import com.sparta.springboards.repository.CommentLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.sparta.springboards.exception.ErrorCode.*;

@Service

//final 또는 @NotNull 이 붙은 필드의 생성자를 자동 생성. 주로 의존성 주입(Dependency Injection) 편의성을 위해서 사용
@RequiredArgsConstructor

public class BoardService {

    //의존성 주입
    private final BoardRepository boardRepository;
    private final BoardLikeRepository boardLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    //private final UserRepository userRepository;
    //private final JwtUtil jwtUtil;

    //게시글 작성
    //클래스나 메서드에 붙여줄 경우, 해당 범위 내 메서드가 트랜잭션(데이터베이스 관리 시스템 또는 유사한 시스템에서 상호작용의 단위(더 이상 쪼개질 수 없는 최소의 연산))이 되도록 보장
    //사용 이유?
    //1. 해당 메서드를 실행하는 도중 메서드 값을 수정/삭제하려는 시도가 들어와도, 값의 신뢰성이 보장
    //2. 연산 도중 오류가 발생해도, rollback 해서 DB에 해당 결과가 반영되지 않도록 함
    //예시: 결제는 다른 사람과 독립적으로 이루어지며, 과정 중에 다른 연산이 끼어들 수 없다. 오류가 생긴 경우 연산을 취소하고 원래대로 되돌린다. 성공할 경우 결과를 반영한다
    @Transactional   //업데이트를 할 때, DB에 반영이 되는 것을 스프링에게 알려줌??
    //BoardResponseDto 반환 타입, createBoard 메소드 명
    //BoardRequestDto: 넘어오는 데이터를 받아주는 객체, HttpServletRequest request 객체: 누가 로그인 했는지 알기위한 토큰을 담고 있음
    public BoardResponseDto createBoard(BoardRequestDto requestDto, User user) {  //HttpServletRequest request?
        if(requestDto.getCategory().equals("TIL") || requestDto.getCategory().equals("Spring") || requestDto.getCategory().equals("Java")){
            Board board = boardRepository.save(new Board(requestDto, user));
            return new BoardResponseDto(board);
        }else{
            throw new CustomException(NOT_EXIST_CATEGORY);
        }
    }


    //전체 게시글 목록 조회
    //(readOnly = true): JPA 를 사용할 경우, 변경감지 작업을 수행하지 않아 성능상의 이점
    @Transactional(readOnly = true)
    //BoardResponseDto 를 List 로 반환하는 타입, getListBoards 메소드 명, () 전부 Client 에게로 반환하므로 비워둠
    public List<BoardResponseDto> getListBoards(User user, String category) {

        if(category.equals("TIL") || category.equals("Spring") || category.equals("Java")){

            List<Board> boardList =boardRepository.findAllByCategoryOrderByCreatedAtDesc(category);
            List<BoardResponseDto> boardResponseDto = new ArrayList<>();

            for (Board board : boardList) {
                List<CommentResponseDto> commentList = new ArrayList<>();
                for (Comment comment : board.getComments()) {
                    commentList.add(new CommentResponseDto(comment, commentLikeRepository.countAllByCommentId(comment.getId())));
                }
                boardResponseDto.add(new BoardResponseDto(
                        board,
                        commentList,
                        (checkBoardLike(board.getId(), user))));
            }
            return boardResponseDto;
        } else {
            //boardRepository 와 연결해서, 모든 데이터들을 내림차순으로, List 타입으로 객체 Board 에 저장된 데이터들을 boardList 안에 담는다
            List<Board> boardList =  boardRepository.findAllByOrderByCreatedAtDesc();      //주의. boards 와 board
            //boardResponseDto 를 새롭게 만든다 --> 텅 빈 상태 (빈 주머니 상태?)
            List<BoardResponseDto> boardResponseDto = new ArrayList<>();

            //반복문을 이용하여, boardList 에 담긴 데이터들을 객체 Board 로 모두 옮긴다
            for (Board board : boardList) {

                List<CommentResponseDto> commentList = new ArrayList<>();
                for (Comment comment : board.getComments()) {

                    commentList.add(new CommentResponseDto(comment, commentLikeRepository.countAllByCommentId(comment.getId())));
                }

                //board 를 새롭게 BoardResponseDto 로 옮겨담고, BoardResponseDto 를 boardResponseDto 안에 추가(add)한다
                boardResponseDto.add(new BoardResponseDto(
                        board,
                        commentList,
                        // 해당 회원의 해당 게시글 좋아요 여부
                        (checkBoardLike(board.getId(), user))));
            }
            //최종적으로 옮겨담아진 boardResponseDto 를 반환
            return boardResponseDto;
        }
    }

    //선택한 게시글 조회
    @Transactional(readOnly = true)
    //BoardResponseDto 반환 타입, getBoard 메소드 명, Long id: 담을 데이터
    public BoardResponseDto getBoard(Long id, User user) {
        //Board: Entity 명, boardRepository 와 연결해서, id 를 찾는다
        Board board = boardRepository.findById(id).orElseThrow(
                //매개변수가 의도치 않는 상황 유발시
                () -> new CustomException(NOT_FOUND_USER)
        );

        List<CommentResponseDto> commentList = new ArrayList<>();
        for (Comment comment : board.getComments()) {
            commentList.add(new CommentResponseDto(comment,commentLikeRepository.countAllByCommentId(comment.getId())));
        }

        //데이터가 들어간 객체 board 를 BoardResponseDto 로 반환
        return new BoardResponseDto(
                board,
                commentList,
                // 해당 회원의 해당 게시글 좋아요 여부
                (checkBoardLike(board.getId(), user)));
    }

    //선택한 게시글 수정(변경)
    @Transactional
    //BoardResponseDto 반환 타입, updateBoard 메소드 명
    //Long id: 담을 데이터, BoardRequestDto: 넘어오는 데이터를 받아주는 객체, HttpServletRequest request 객체: 누가 로그인 했는지 알기위한 토큰을 담고 있음
    public BoardResponseDto updateBoard(Long id, BoardRequestDto requestDto, User user) {    //HttpServletRequest request?

        Board board;    //board 를 사용하기위해서는 이런 변수 선언 필요함

        //user 의 권한이 ADMIN 와 같다면,
        if(user.getRole().equals(UserRoleEnum.ADMIN)) {
            board = boardRepository.findById(id).orElseThrow(
                    () -> new CustomException(NOT_FOUND_BOARD)
                    //() -> new RequestException(ErrorCoded .게시글이_존재하지_않습니다_400)
            );
        } else {
            //user 의 권한이 ADMIN 이 아니라면, 아이디가 같은 유저만 수정 가능
            board = boardRepository.findByIdAndUserId(id, user.getId()).orElseThrow(
                    () -> new CustomException(NOT_FOUND_USER)
            );
        }

        board.update(requestDto);

        List<CommentResponseDto> commentList = new ArrayList<>();
        for (Comment comment : board.getComments()) {
            commentList.add(new CommentResponseDto(comment, commentLikeRepository.countAllByCommentId(comment.getId())));
        }

        if(requestDto.getCategory().equals("TIL") || requestDto.getCategory().equals("Spring") || requestDto.getCategory().equals("Java")){
            return new BoardResponseDto(
                    board,
                    commentList,
                    // 해당 회원의 해당 게시글 좋아요 여부
                    (checkBoardLike(board.getId(), user)));
        }else{
            throw new CustomException(NOT_EXIST_CATEGORY);
        }
        //데이터가 들어간 객체 board 를 BoardResponseDto 로 반환


    }

    //선택한 게시글 삭제
    @Transactional
    //MsgResponseDto 반환 타입, deleteBoard 메소드 명
    //Long id: 담을 데이터, HttpServletRequest request 객체: 누가 로그인 했는지 알기위한 토큰을 담고 있음
    public void deleteBoard (Long id, User user) {    //HttpServletRequest request?

        Board board;    //board 를 사용하기위해서는 이런 변수 선언 필요함

        //user 의 권한이 ADMIN 와 같다면,
        if(user.getRole().equals(UserRoleEnum.ADMIN)) {
            board = boardRepository.findById(id).orElseThrow(
                    () -> new CustomException(NOT_FOUND_BOARD)
                    //() -> new RequestException(ErrorCode.게시글이_존재하지_않습니다_400)
            );

        } else {
            //user 의 권한이 ADMIN 이 아니라면, 아이디가 같은 유저만 수정 가능
            board = boardRepository.findByIdAndUserId(id, user.getId()).orElseThrow(
                    () -> new CustomException(NOT_FOUND_USER)
            );
        }

        boardRepository.delete(board);

        // fetch, 영속성 컨텍스트

    }

    @Transactional(readOnly = true)
    public boolean checkBoardLike(Long boardId, User user) {
        // 해당 회원의 좋아요 여부 확인
        Optional<BoardLike> boardLike = boardLikeRepository.findByBoardIdAndUserId(boardId, user.getId());
        return boardLike.isPresent();
    }

    @Transactional
    public MsgResponseDto saveBoardLike(Long boardId, User user) {
        // 입력 받은 게시글 id와 일치하는 DB 조회
        Board board = boardRepository.findById(boardId).orElseThrow(
                () -> new NullPointerException("게시글이 존재하지 않습니다.")
        );

        // 해당 회원의 좋아요 여부를 확인하고 비어있으면 좋아요, 아니면 좋아요 취소
        if (!checkBoardLike(boardId, user)) {
            boardLikeRepository.saveAndFlush(new BoardLike(board, user));
            return new MsgResponseDto("좋아요 완료", HttpStatus.OK.value());
        } else {
            boardLikeRepository.deleteByBoardIdAndUserId(boardId, user.getId());
            return new MsgResponseDto("좋아요 취소", HttpStatus.OK.value());
        }
    }
}