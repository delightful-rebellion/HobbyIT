package com.a505.hobbyit.user.domain;

import com.a505.hobbyit.application.domain.Application;
import com.a505.hobbyit.article.domain.Article;
import com.a505.hobbyit.comment.domain.Comment;
import com.a505.hobbyit.grouparticle.domain.GroupArticle;
import com.a505.hobbyit.groupcomment.domain.GroupComment;
import com.a505.hobbyit.groupuser.domain.GroupUser;
import com.a505.hobbyit.postit.domain.Postit;
import com.a505.hobbyit.user.enums.UserPrivilege;
import com.a505.hobbyit.user.enums.UserState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user")
public class User {
    @Column(name = "u_id", nullable = false)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String password;
    @Column
    private String intro;

    @Column(nullable = false)
    private LocalDateTime enrollDate;

    @Column
    private LocalDateTime resignedRequestDate;

    @Column(nullable = false)
    private int ownedGroupNum;

    @Column(nullable = false)
    private int point;

    @Column(nullable = false)
    private String profileImg;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserPrivilege privilege = UserPrivilege.GENERAL;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserState state = UserState.ACTIVE;

    @OneToMany(mappedBy = "user")
    private List<Application> applications = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<GroupUser> groupUsers = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<GroupArticle> groupArticles = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<GroupComment> groupComments = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Article> articles = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Postit> postits = new ArrayList<>();
}
