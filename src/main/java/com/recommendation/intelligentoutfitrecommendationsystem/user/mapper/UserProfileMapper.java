package com.recommendation.intelligentoutfitrecommendationsystem.user.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserBodyData;
import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserPreferences;
import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserProfileMapper {

    UserProfile findProfileByUserId(@Param("userId") Long userId);

    void insertProfile(UserProfile profile);

    void updateProfile(UserProfile profile);

    UserBodyData findBodyDataByUserId(@Param("userId") Long userId);

    void insertBodyData(UserBodyData bodyData);

    void updateBodyData(UserBodyData bodyData);

    UserPreferences findPreferencesByUserId(@Param("userId") Long userId);

    void insertPreferences(UserPreferences preferences);

    void updatePreferences(UserPreferences preferences);
}
