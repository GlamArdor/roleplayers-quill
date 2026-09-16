package com.glamardor.roleplayersquill.text;

import net.minecraft.text.Text;

import java.time.LocalDate;

/**
 * The date as the roleplay server reckons it.
 *
 * <p>A month out there is a year in the setting, so the calendar runs twelve times as fast as this
 * one. September 2026 is the year 226; October 2026 is 227. The day of the month keeps its number
 * and is called a unia rather than a day.
 *
 * <p>So 13 September 2026 is "13 уния 226 года", and 10 October 2026 is "10 уния 227 года".
 */
public final class ServerDate {
	/**
	 * The year the count was fixed against.
	 *
	 * <p>Chosen so that the ninth month of 2026 comes out as 226, which is what the server says it
	 * is. Everything else follows from twelve years to the calendar year.
	 */
	private static final int EPOCH_YEAR = 2026;
	private static final int EPOCH_OFFSET = 217;

	private ServerDate() {
	}

	/** What year it is on the server today. */
	public static int year(LocalDate date) {
		return (date.getYear() - EPOCH_YEAR) * 12 + date.getMonthValue() + EPOCH_OFFSET;
	}

	/** Today, written the way the server writes it: "13 уния 226 г." */
	public static String today() {
		return of(LocalDate.now());
	}

	public static String of(LocalDate date) {
		return Text.translatable("roleplayersquill.date.format", date.getDayOfMonth(), year(date)).getString();
	}

	/**
	 * Today, in the form that follows a preposition: "от 13 унии 226 г."
	 *
	 * <p>Russian declines it and a decree is dated "от" something, so the plain form reads wrong
	 * there. Kept as a translation of its own rather than as a rule, because the rule is different
	 * in every language and there is no rule at all in English.
	 */
	public static String todayAfterPreposition() {
		LocalDate date = LocalDate.now();
		return Text.translatable("roleplayersquill.date.format.of", date.getDayOfMonth(), year(date)).getString();
	}
}
