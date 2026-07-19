package utils

import (
	"fmt"
	"net"
	"regexp"
	"strings"
)

func IsValidEmail(email string) bool {
	re := regexp.MustCompile(`(?i)^[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,24}$`)
	return re.MatchString(email)
}

func VerifyEmailDomain(email string) (bool, error) {
	parts := strings.Split(email, "@")
	if len(parts) != 2 {
		return false, fmt.Errorf("invalid email format")
	}
	domain := parts[1]

	mx, err := net.LookupMX(domain)
	if err != nil {
		return false, err
	}

	return len(mx) > 0, nil
}

func FormatAge(days *int) string {
	if days == nil {
		return ""
	}
	d := *days
	if d < 30 {
		return fmt.Sprintf("%d days", d)
	}
	if d < 365 {
		months := float64(d) / 30.0
		if months < 1.5 {
			return "1 month"
		}
		return fmt.Sprintf("%.0f months", months)
	}
	years := float64(d) / 365.0
	if years < 1.05 {
		return "1 year"
	}
	return fmt.Sprintf("%.1f years", years)
}

func DerefString(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
